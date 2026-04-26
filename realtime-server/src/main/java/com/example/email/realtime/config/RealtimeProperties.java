package com.example.email.realtime.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class RealtimeProperties {

    private Jwt jwt = new Jwt();
    private String instanceId = "rt-local";
    private int sessionTtlSeconds = 300;

    @Data public static class Jwt {
        private String secret;
    }
}
