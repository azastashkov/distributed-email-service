package com.example.email.web.search;

import com.example.email.web.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class OpenSearchConfig {

    private final AppProperties props;

    @Bean
    public OpenSearchClient openSearchClient() {
        URI uri = URI.create(props.getOpensearch().getUri());
        HttpHost host = new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
        OpenSearchTransport transport = ApacheHttpClient5TransportBuilder
                .builder(host)
                .setMapper(new JacksonJsonpMapper())
                .build();
        log.info("OpenSearch client built for {}", uri);
        return new OpenSearchClient(transport);
    }
}
