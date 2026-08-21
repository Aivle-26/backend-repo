package com.aivle26.aipm.Dto.project;

import java.util.List;

public record ProjectSearchResponse(
        Long projectId,
        String query,
        int totalCount,
        List<SearchResult> results
) {
    public record SearchResult(
            String type,
            String id,
            String title,
            String summary
    ) {
    }
}
