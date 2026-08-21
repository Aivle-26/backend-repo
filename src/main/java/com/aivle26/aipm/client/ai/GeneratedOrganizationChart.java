package com.aivle26.aipm.client.ai;

import com.aivle26.aipm.Dto.project.OrganizationChartGenerateResponse;

public record GeneratedOrganizationChart(
        OrganizationChartGenerateResponse response,
        byte[] image
) {
    public GeneratedOrganizationChart {
        image = image.clone();
    }

    @Override
    public byte[] image() {
        return image.clone();
    }
}
