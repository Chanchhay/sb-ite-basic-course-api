package kh.edu.istad.ite.features.admin.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import kh.edu.istad.ite.features.admin.dto.response.AdminAuditLogResponse;
import kh.edu.istad.ite.features.admin.entity.AdminAuditLog;
import kh.edu.istad.ite.features.admin.mapper.AdminAuditLogMapper;
import kh.edu.istad.ite.features.admin.repository.AdminAuditLogRepository;
import kh.edu.istad.ite.features.admin.service.AdminAuditLogService;
import kh.edu.istad.ite.features.admin.specification.AdminAuditLogSpecifications;
import kh.edu.istad.ite.shared.enums.AdminActionType;
import kh.edu.istad.ite.shared.enums.AuditTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAuditLogServiceImpl implements AdminAuditLogService {

    private static final String UNKNOWN_ACTOR = "UNKNOWN";
    private static final int REASON_MAX_LENGTH = 500;
    private static final int LABEL_MAX_LENGTH = 255;
    private static final int USER_AGENT_MAX_LENGTH = 255;

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final AdminAuditLogMapper adminAuditLogMapper;

    @Override
    @Transactional
    public void record(
            AdminActionType actionType,
            AuditTargetType targetType,
            UUID targetId,
            String targetLabel,
            String reason
    ) {
        recordStateChange(actionType, targetType, targetId, targetLabel, null, null, reason);
    }

    @Override
    @Transactional
    public void recordStateChange(
            AdminActionType actionType,
            AuditTargetType targetType,
            UUID targetId,
            String targetLabel,
            String previousState,
            String newState,
            String reason
    ) {
        AdminAuditLog auditLog = new AdminAuditLog();
        auditLog.setActorId(currentActorId());
        auditLog.setActorUsername(currentActorUsername());
        auditLog.setActionType(actionType);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setTargetLabel(truncate(targetLabel, LABEL_MAX_LENGTH));
        auditLog.setPreviousState(previousState);
        auditLog.setNewState(newState);
        auditLog.setIpAddress(currentIpAddress());
        auditLog.setUserAgent(truncate(currentUserAgent(), USER_AGENT_MAX_LENGTH));
        auditLog.setCreatedAt(LocalDateTime.now());

        adminAuditLogRepository.save(auditLog);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminAuditLogResponse> getAuditLogs(
            AdminActionType actionType,
            AuditTargetType targetType,
            UUID targetId,
            String actorId,
            LocalDateTime from,
            LocalDateTime to,
            String keyword,
            Pageable pageable
    ) {
        var spec = AdminAuditLogSpecifications.withFilters(
                actionType, targetType, targetId, actorId, from, to, keyword
        );

        return adminAuditLogRepository.findAll(spec, pageable).map(adminAuditLogMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminAuditLogResponse getAuditLog(UUID auditLogId) {
        return adminAuditLogRepository.findById(auditLogId)
                .map(adminAuditLogMapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audit log has not been found"));
    }

    private String currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken().getSubject();
        }

        return UNKNOWN_ACTOR;
    }

    private String currentActorUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            String username = jwtAuthenticationToken.getToken().getClaimAsString("preferred_username");

            if (StringUtils.hasText(username)) {
                return username;
            }

            String email = jwtAuthenticationToken.getToken().getClaimAsString("email");
            if (StringUtils.hasText(email)) {
                return email;
            }
        }

        return UNKNOWN_ACTOR;
    }

    private String currentIpAddress() {
        HttpServletRequest request = currentRequest();

        if (request == null) {
            return null;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    private String currentUserAgent() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getHeader("User-Agent");
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }

        return null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
