package com.example.email.web.search;

import com.example.email.common.dto.SearchHit;
import com.example.email.common.event.EmailEvent;
import com.example.email.web.config.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.HighlightField;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailSearchService {

    private final OpenSearchClient client;
    private final AppProperties props;

    @PostConstruct
    void ensureIndex() {
        String index = props.getOpensearch().getIndex();
        try {
            boolean exists = client.indices().exists(ExistsRequest.of(b -> b.index(index))).value();
            if (!exists) {
                Map<String, Property> properties = new LinkedHashMap<>();
                properties.put("userId",     Property.of(p -> p.keyword(k -> k)));
                properties.put("emailId",    Property.of(p -> p.keyword(k -> k)));
                properties.put("folderId",   Property.of(p -> p.keyword(k -> k)));
                properties.put("subject",    Property.of(p -> p.text(t -> t)));
                properties.put("body",       Property.of(p -> p.text(t -> t)));
                properties.put("fromAddr",   Property.of(p -> p.keyword(k -> k)));
                properties.put("receivedAt", Property.of(p -> p.date(d -> d)));
                client.indices().create(c -> c
                        .index(index)
                        .mappings(m -> m.properties(properties)));
                log.info("Created OpenSearch index '{}'", index);
            }
        } catch (IOException e) {
            log.warn("OpenSearch index check/create failed: {}", e.getMessage());
        }
    }

    public void indexFromEvent(EmailEvent.EmailCreated created) throws IOException {
        Map<String, Object> doc = new HashMap<>();
        doc.put("userId", created.userId().toString());
        doc.put("emailId", created.emailId().toString());
        doc.put("folderId", created.folderId().toString());
        doc.put("subject", created.subject());
        doc.put("body", created.preview()); // event carries preview; full body is fine here for demo
        doc.put("fromAddr", created.fromAddr());
        doc.put("receivedAt", created.receivedAt().toString());

        IndexResponse resp = client.index(b -> b
                .index(props.getOpensearch().getIndex())
                .id(created.emailId().toString())
                .document(doc)
                .refresh(org.opensearch.client.opensearch._types.Refresh.True));
        log.debug("Indexed {} -> {}", created.emailId(), resp.result());
    }

    public void deleteFromIndex(UUID emailId) {
        try {
            client.delete(b -> b.index(props.getOpensearch().getIndex()).id(emailId.toString())
                    .refresh(org.opensearch.client.opensearch._types.Refresh.True));
        } catch (IOException e) {
            log.warn("OpenSearch delete failed for {}: {}", emailId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<SearchHit> search(UUID userId, String q, int from, int size) throws IOException {
        Query userFilter = Query.of(qb -> qb.term(t -> t.field("userId").value(v -> v.stringValue(userId.toString()))));
        Query textQuery = Query.of(qb -> qb.multiMatch(m -> m
                .query(q)
                .fields("subject^2", "body")));

        SearchResponse<Map> resp = client.search(s -> s
                .index(props.getOpensearch().getIndex())
                .from(from)
                .size(size)
                .query(b -> b.bool(bool -> bool.must(textQuery).filter(userFilter)))
                .highlight(h -> h.fields("body", HighlightField.of(f -> f.fragmentSize(120).numberOfFragments(1)))),
                Map.class);

        List<SearchHit> out = new ArrayList<>();
        for (var hit : resp.hits().hits()) {
            Map<String, Object> src = hit.source();
            String subject = src == null ? "" : (String) src.getOrDefault("subject", "");
            String snippet = "";
            if (hit.highlight() != null && hit.highlight().get("body") != null && !hit.highlight().get("body").isEmpty()) {
                snippet = hit.highlight().get("body").get(0);
            } else if (src != null) {
                String body = (String) src.getOrDefault("body", "");
                snippet = body.length() > 200 ? body.substring(0, 200) : body;
            }
            UUID id = UUID.fromString(hit.id());
            double score = hit.score() == null ? 0.0 : hit.score();
            out.add(new SearchHit(id, subject, snippet, score));
        }
        return out;
    }
}
