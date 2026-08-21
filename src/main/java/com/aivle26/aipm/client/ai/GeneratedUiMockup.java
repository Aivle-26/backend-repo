package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Dto.project.UiMockupGenerateResponse;

public record GeneratedUiMockup(
        UiMockupGenerateResponse response,
        byte[] image
) {
    public GeneratedUiMockup {
        image = image.clone();
    }

    @Override
    public byte[] image() {
        return image.clone();
    }
}
