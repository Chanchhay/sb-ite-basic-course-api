package kh.edu.istad.ite.features.admin.controller;

import kh.edu.istad.ite.features.admin.dto.response.AdminAuditLogResponse;
import kh.edu.istad.ite.features.admin.service.AdminAuditLogService;
import kh.edu.istad.ite.shared.enums.AdminActionType;
import kh.edu.istad.ite.shared.enums.AuditTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AdminAuditLogService adminAuditLogService;

    @GetMapping
    public Page<AdminAuditLogResponse> getAuditLogs(
            @RequestParam(required = false) AdminActionType actionType,
            @RequestParam(required = false) AuditTargetType targetType,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return adminAuditLogService.getAuditLogs(
                actionType, targetType, targetId, actorId, from, to, keyword, pageable
        );
    }

    @GetMapping("/{auditLogId}")
    public AdminAuditLogResponse getAuditLog(@PathVariable UUID auditLogId) {
        return adminAuditLogService.getAuditLog(auditLogId);
    }
}
