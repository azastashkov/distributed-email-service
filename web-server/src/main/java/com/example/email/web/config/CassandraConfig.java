package com.example.email.web.config;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class CassandraConfig {

    private final AppProperties props;
    private CqlSession session;

    @Bean(destroyMethod = "")
    public CqlSession cqlSession() {
        var cassandra = props.getCassandra();
        List<InetSocketAddress> addresses = cassandra.getContactPoints().stream()
                .map(s -> {
                    String[] parts = s.split(":");
                    return new InetSocketAddress(parts[0], parts.length > 1 ? Integer.parseInt(parts[1]) : 9042);
                })
                .toList();

        log.info("Connecting to Cassandra: contactPoints={} localDc={} keyspace={}",
                addresses, cassandra.getLocalDc(), cassandra.getKeyspace());

        CqlSessionBuilder builder = CqlSession.builder()
                .addContactPoints(addresses)
                .withLocalDatacenter(cassandra.getLocalDc())
                .withKeyspace(cassandra.getKeyspace());

        this.session = builder.build();
        return this.session;
    }

    @PreDestroy
    public void close() {
        if (session != null) session.close();
    }
}
