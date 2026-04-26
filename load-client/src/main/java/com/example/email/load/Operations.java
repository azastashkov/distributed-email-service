package com.example.email.load;

import com.example.email.load.ScenarioRunner.VirtualUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

final class Operations {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private Operations() {}

    static void sendEmail(WebClient web, VirtualUser sender, List<String> directory, Random rnd, boolean withAttachment) throws Exception {
        String to = pickRecipient(sender.email, directory, rnd);
        String subject = "load test " + UUID.randomUUID().toString().substring(0, 8);
        String body = "Generated load body. lorem ipsum dolor sit amet, " + UUID.randomUUID();

        Map<String, Object> draft = withAttachment
                ? Map.of("to", List.of(to), "cc", List.of(), "bcc", List.of(),
                         "subject", subject, "body", body, "attachmentNames", List.of("payload.bin"))
                : Map.of("to", List.of(to), "cc", List.of(), "bcc", List.of(),
                         "subject", subject, "body", body, "attachmentNames", List.of());

        Map<?, ?> draftResp = web.post().uri("/api/v1/emails/draft")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sender.token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(draft)
                .retrieve().bodyToMono(Map.class).timeout(Duration.ofSeconds(10)).block();
        if (draftResp == null) throw new RuntimeException("draft failed");
        String emailId = (String) draftResp.get("emailId");

        if (withAttachment) {
            List<Map<String, Object>> uploads = (List<Map<String, Object>>) draftResp.get("attachmentUploads");
            for (Map<String, Object> up : uploads) {
                String url = (String) up.get("presignedUrl");
                byte[] bytes = new byte[100 * 1024];
                rnd.nextBytes(bytes);
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                        .build();
                HttpResponse<Void> resp = HTTP.send(req, HttpResponse.BodyHandlers.discarding());
                if (resp.statusCode() / 100 != 2) {
                    throw new RuntimeException("attachment upload failed " + resp.statusCode());
                }
            }
        }

        web.post().uri("/api/v1/emails/" + emailId + "/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sender.token)
                .retrieve().toBodilessEntity().timeout(Duration.ofSeconds(10)).block();
    }

    static void listInbox(WebClient web, VirtualUser u) {
        UUID inboxId = inboxOf(web, u);
        if (inboxId == null) return;
        web.get().uri("/api/v1/folders/{id}/emails?limit=20", inboxId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + u.token)
                .retrieve().bodyToMono(Map.class).timeout(Duration.ofSeconds(10)).block();
    }

    static void getOneInboxEmail(WebClient web, VirtualUser u) {
        UUID inboxId = inboxOf(web, u);
        if (inboxId == null) return;
        Map<?, ?> page = web.get().uri("/api/v1/folders/{id}/emails?limit=5", inboxId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + u.token)
                .retrieve().bodyToMono(Map.class).timeout(Duration.ofSeconds(10)).block();
        if (page == null) return;
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
        if (items == null || items.isEmpty()) return;
        String emailId = (String) items.get(0).get("emailId");
        web.get().uri("/api/v1/emails/" + emailId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + u.token)
                .retrieve().bodyToMono(Map.class).timeout(Duration.ofSeconds(10)).block();
    }

    static void markInboxRead(WebClient web, VirtualUser u) {
        UUID inboxId = inboxOf(web, u);
        if (inboxId == null) return;
        Map<?, ?> page = web.get().uri("/api/v1/folders/{id}/emails?limit=5", inboxId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + u.token)
                .retrieve().bodyToMono(Map.class).timeout(Duration.ofSeconds(10)).block();
        if (page == null) return;
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
        if (items == null || items.isEmpty()) return;
        for (Map<String, Object> it : items) {
            String emailId = (String) it.get("emailId");
            boolean isRead = Boolean.TRUE.equals(it.get("isRead"));
            web.patch().uri("/api/v1/emails/" + emailId + "/read")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + u.token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("isRead", !isRead))
                    .retrieve().toBodilessEntity().timeout(Duration.ofSeconds(10)).block();
            return;
        }
    }

    static void listUnread(WebClient web, VirtualUser u) {
        web.get().uri("/api/v1/emails?status=unread&limit=20")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + u.token)
                .retrieve().bodyToMono(Map.class).timeout(Duration.ofSeconds(10)).block();
    }

    static void search(WebClient web, VirtualUser u) {
        web.get().uri("/api/v1/search?q=load&limit=10")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + u.token)
                .retrieve().bodyToMono(Map.class).timeout(Duration.ofSeconds(10)).block();
    }

    private static UUID inboxOf(WebClient web, VirtualUser u) {
        List<Map<String, Object>> folders = (List<Map<String, Object>>) web.get().uri("/api/v1/folders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + u.token)
                .retrieve().bodyToMono(List.class).timeout(Duration.ofSeconds(10)).block();
        if (folders == null) return null;
        return folders.stream()
                .filter(f -> "INBOX".equals(f.get("name")))
                .findFirst()
                .map(f -> UUID.fromString((String) f.get("folderId")))
                .orElse(null);
    }

    private static String pickRecipient(String selfEmail, List<String> dir, Random rnd) {
        if (dir.isEmpty()) return selfEmail;
        int attempts = 5;
        while (attempts-- > 0) {
            String c = dir.get(rnd.nextInt(dir.size()));
            if (!c.equals(selfEmail)) return c;
        }
        return selfEmail;
    }
}
