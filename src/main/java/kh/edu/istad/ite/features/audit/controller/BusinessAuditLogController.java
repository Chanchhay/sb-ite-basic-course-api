package kh.edu.istad.ite.features.audit.controller;

import kh.edu.istad.ite.features.audit.dto.response.BusinessAuditLogResponse;
import kh.edu.istad.ite.features.audit.service.BusinessAuditService;
import kh.edu.istad.ite.shared.dto.PageResponse;
import kh.edu.istad.ite.shared.enums.BusinessAuditAction;
import kh.edu.istad.ite.shared.enums.BusinessAuditTarget;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * A shop's own audit log.
 *
 * The business is resolved from the caller and never taken from the request:
 * there is no path variable and no query parameter that names one, so reading
 * another shop's log is not something a caller can ask for.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class BusinessAuditLogController {

    private final BusinessAuditService auditService;
    private final BusinessHelper businessHelper;

    @GetMapping
    public PageResponse<BusinessAuditLogResponse> getAuditLogs(
            @RequestParam(required = false) BusinessAuditAction actionType,
            @RequestParam(required = false) BusinessAuditTarget targetType,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return PageResponse.from(auditService.getAuditLogs(
                businessHelper.currentBusiness().getId(),
                actionType, targetType, actorId, from, to, keyword, pageable));
    }
}
