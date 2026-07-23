package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.PlanningDocumentExtractResponse;
import java.util.List;

public interface PlanningAgentClient {
    PlanningDocumentExtractResponse extractDocuments(List<StoredDocumentFile> files, boolean enableLlm);
}
