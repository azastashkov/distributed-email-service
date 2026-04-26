package com.example.email.load;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "load")
@Data
public class LoadProperties {
    private String targetWebUrl;
    private String targetWsUrl;
    private int users;
    private int durationSeconds;
    private int rampSeconds;
    private int startDelaySeconds;
    private Map<String, Integer> mix = new LinkedHashMap<>();
}
