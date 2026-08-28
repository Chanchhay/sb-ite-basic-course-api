package kh.edu.istad.ite.features.register.dto.response;

import kh.edu.istad.ite.shared.dto.PageResponse;


public record RegisterSessionSearchResponse(
        PageResponse<RegisterSessionResponse> page,
        RegisterSessionMetrics metrics
) {
}
