package com.example.email.web.search;

import com.example.email.common.dto.SearchHit;
import com.example.email.web.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final EmailSearchService searchService;

    public record SearchResponse(List<SearchHit> hits) {}

    @GetMapping
    public ResponseEntity<SearchResponse> search(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @RequestParam("q") String q,
                                                 @RequestParam(defaultValue = "20") int limit,
                                                 @RequestParam(defaultValue = "0") int from) throws IOException {
        var hits = searchService.search(user.userId(), q, from, Math.min(limit, 100));
        return ResponseEntity.ok(new SearchResponse(hits));
    }
}
