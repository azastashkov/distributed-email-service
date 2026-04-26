package com.example.email.web.search;

import com.example.email.common.event.EmailEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@ConditionalOnProperty(value = "app.search-indexer-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class SearchIndexerConsumer {

    private final EmailSearchService searchService;

    @KafkaListener(topics = "email-events", groupId = "search-indexer")
    public void onEvent(EmailEvent event) {
        try {
            switch (event) {
                case EmailEvent.EmailCreated created -> searchService.indexFromEvent(created);
                case EmailEvent.EmailDeleted deleted -> searchService.deleteFromIndex(deleted.emailId());
                case EmailEvent.EmailReadChanged ignored -> { /* read flag not indexed */ }
            }
        } catch (IOException e) {
            log.warn("Failed to index event {}: {}", event, e.getMessage());
        }
    }
}
