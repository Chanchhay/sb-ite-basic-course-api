package kh.edu.istad.ite.features.register.dto.response;

import kh.edu.istad.ite.shared.dto.PageResponse;

/**
 * A page of register sessions, and the totals for everything the filter matched.
 *
 * The two travel together because they are read together and must agree: a
 * screen that pages the rows from one call and totals them from another can
 * show a total that belongs to a filter the reader has already changed.
 */
public record RegisterSessionSearchResponse(
        PageResponse<RegisterSessionResponse> page,
        RegisterSessionMetrics metrics
) {
}
