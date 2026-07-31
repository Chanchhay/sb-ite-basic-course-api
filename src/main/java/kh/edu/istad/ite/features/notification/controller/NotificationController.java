package kh.edu.istad.ite.features.notification.controller;

import jakarta.validation.Valid;
import kh.edu.istad.ite.config.security.SecurityUtils;
import kh.edu.istad.ite.features.notification.dto.*;
import kh.edu.istad.ite.features.notification.entity.NotificationType;
import kh.edu.istad.ite.features.notification.service.NotificationCommandService;
import kh.edu.istad.ite.features.notification.service.NotificationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationCommandService commandService;
    private final NotificationQueryService queryService;

    private UUID currentUserId() {
        return UUID.fromString(SecurityUtils.extractUserId());
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateNotificationResponse create(@Valid @RequestBody CreateNotificationRequest request) {
        return commandService.send(currentUserId(), request);   // ADJUST method name
    }

    @GetMapping("/received")
    public Page<ReceivedNotificationResponse> received(
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) Boolean isRead,
            @PageableDefault(size = 20, sort = "createdDate",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        UUID me = currentUserId();
        return queryService.getReceived(me, type, isRead, pageable);
    }

    @GetMapping("/received/unread-count")
    public UnreadCountResponse unreadCount(@RequestParam String receiverId) {
        return new UnreadCountResponse(queryService.getUnreadCount(currentUserId(), receiverId));
    }

    @PatchMapping("/received/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable UUID id) {
        commandService.markAsRead(currentUserId(), id);
    }

    @PatchMapping("/received/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@RequestParam String receiverId) {
        commandService.markAllAsRead(currentUserId(), receiverId);
    }

    @PutMapping("/received/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        commandService.delete(currentUserId(), id);
    }

    @DeleteMapping("/received/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hardDelete(@PathVariable UUID id) {
        commandService.hardDelete(currentUserId(), id);
    }
}
