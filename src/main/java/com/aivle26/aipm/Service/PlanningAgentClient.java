package com.aivle26.aipm.Service;

import com.aivle26.aipm.Dto.PlanningDocumentExtractResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PlanningAgentClient {
    PlanningDocumentExtractResponse extractDocuments(List<MultipartFile> files, boolean enableLlm);
}
