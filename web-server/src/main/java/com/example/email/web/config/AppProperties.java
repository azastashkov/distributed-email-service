package com.example.email.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private Cassandra cassandra = new Cassandra();
    private Jwt jwt = new Jwt();
    private Minio minio = new Minio();
    private OpenSearch opensearch = new OpenSearch();
    private Cache cache = new Cache();
    private String instanceId = "web-local";
    private boolean searchIndexerEnabled = true;

    @Data public static class Cassandra {
        private List<String> contactPoints;
        private String localDc;
        private String keyspace;
    }

    @Data public static class Jwt {
        private String secret;
        private int ttlMinutes;
    }

    @Data public static class Minio {
        private String endpoint;
        private String publicEndpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
        private String region;
        private int presignTtlMinutes;
    }

    @Data public static class OpenSearch {
        private String uri;
        private String index;
    }

    @Data public static class Cache {
        private int emailTtlSeconds;
    }
}
