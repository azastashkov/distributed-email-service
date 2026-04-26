package com.example.email.load;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScenarioRunner implements CommandLineRunner {

    private final LoadProperties props;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final WsSubscriber wsSubscriber;

    private final List<String> directory = Collections.synchronizedList(new ArrayList<>());
    private ExecutorService pool;
    private WebClient webClient;
    private volatile boolean stop = false;

    @PostConstruct
    void init() {
        HttpClient hc = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(20))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000);
        webClient = WebClient.builder()
                .baseUrl(props.getTargetWebUrl())
                .clientConnector(new ReactorClientHttpConnector(hc))
                .build();
        pool = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PreDestroy
    void shutdown() {
        stop = true;
        if (pool != null) pool.shutdownNow();
    }

    @Override
    public void run(String... args) {
        log.info("Load client starting; will sleep {}s before driving load", props.getStartDelaySeconds());
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(Duration.ofSeconds(props.getStartDelaySeconds()));
                drive();
            } catch (InterruptedException ignored) {}
        });
    }

    private void drive() {
        long endAt = System.nanoTime() + Duration.ofSeconds(props.getDurationSeconds()).toNanos();
        log.info("Ramping {} virtual users over {}s; running for {}s",
                props.getUsers(), props.getRampSeconds(), props.getDurationSeconds());

        AtomicInteger started = new AtomicInteger(0);
        long rampNanosPerUser = Duration.ofSeconds(props.getRampSeconds()).toNanos() / Math.max(1, props.getUsers());

        for (int i = 0; i < props.getUsers(); i++) {
            int idx = i;
            pool.submit(() -> {
                try { Thread.sleep(Duration.ofNanos(rampNanosPerUser * idx)); } catch (InterruptedException ignored) {}
                runUser(idx, endAt);
                started.decrementAndGet();
            });
            started.incrementAndGet();
        }

        // Block main thread until duration elapsed
        while (!stop && System.nanoTime() < endAt) {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }
        stop = true;
        log.info("Load run complete");
    }

    private void runUser(int idx, long endAt) {
        VirtualUser vu = signupOrFail(idx);
        if (vu == null) return;
        directory.add(vu.email);
        wsSubscriber.openFor(vu);

        WeightedPicker picker = new WeightedPicker(props.getMix());
        Random rnd = new Random(System.nanoTime() + idx);

        // simulate realistic per-user inter-arrival
        Duration thinkTime = Duration.ofMillis(500);
        while (!stop && System.nanoTime() < endAt) {
            String op = picker.next(rnd);
            Timer.Sample sample = Timer.start(meterRegistry);
            String status = "OK";
            try {
                executeOp(vu, op, rnd);
            } catch (Exception e) {
                status = "ERR";
                Counter.builder("loadclient.op.errors")
                        .tag("op", op).tag("reason", e.getClass().getSimpleName())
                        .register(meterRegistry).increment();
            } finally {
                sample.stop(Timer.builder("loadclient.op.duration")
                        .tag("op", op).tag("status", status)
                        .publishPercentileHistogram()
                        .register(meterRegistry));
            }
            try { Thread.sleep(thinkTime); } catch (InterruptedException ignored) {}
        }
    }

    private VirtualUser signupOrFail(int idx) {
        String email = "u-" + UUID.randomUUID() + "@load.test";
        Map<String, Object> body = Map.of("email", email, "password", "password123", "displayName", "User-" + idx);
        try {
            Map<?, ?> resp = webClient.post().uri("/api/v1/auth/signup").bodyValue(body)
                    .retrieve().bodyToMono(Map.class).timeout(Duration.ofSeconds(10)).block();
            if (resp == null) return null;
            return new VirtualUser(idx, email, (String) resp.get("token"), UUID.fromString((String) resp.get("userId")));
        } catch (Exception e) {
            log.warn("Signup failed for {}: {}", email, e.toString());
            return null;
        }
    }

    private void executeOp(VirtualUser vu, String op, Random rnd) throws Exception {
        switch (op) {
            case "sendNoAtt"    -> Operations.sendEmail(webClient, vu, directory, rnd, false);
            case "sendWithAtt"  -> Operations.sendEmail(webClient, vu, directory, rnd, true);
            case "listFolder"   -> Operations.listInbox(webClient, vu);
            case "getEmail"     -> Operations.getOneInboxEmail(webClient, vu);
            case "markRead"     -> Operations.markInboxRead(webClient, vu);
            case "listUnread"   -> Operations.listUnread(webClient, vu);
            case "search"       -> Operations.search(webClient, vu);
            default -> log.warn("Unknown op {}", op);
        }
    }

    public static final class VirtualUser {
        public final int idx;
        public final String email;
        public final String token;
        public final UUID userId;

        public VirtualUser(int idx, String email, String token, UUID userId) {
            this.idx = idx; this.email = email; this.token = token; this.userId = userId;
        }
    }

    static final class WeightedPicker {
        private final List<String> ops = new ArrayList<>();
        private final int[] cum;
        private final int total;

        WeightedPicker(Map<String, Integer> mix) {
            int sum = 0;
            int[] c = new int[mix.size()];
            int i = 0;
            for (var e : mix.entrySet()) {
                ops.add(e.getKey());
                sum += e.getValue();
                c[i++] = sum;
            }
            this.cum = c;
            this.total = sum;
        }

        String next(Random rnd) {
            int r = rnd.nextInt(total);
            for (int i = 0; i < cum.length; i++) {
                if (r < cum[i]) return ops.get(i);
            }
            return ops.get(ops.size() - 1);
        }
    }
}
