package kh.edu.istad.ite.features.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.features.notification.entity.NotificationType;

import java.util.List;

public record CreateNotificationRequest(
        @NotBlank @Size(max = 100) String senderId,
        @NotEmpty List<@NotBlank @Size(max = 100) String> receiverIds,
        @NotNull NotificationType type,
        @NotBlank @Size(max = 200) String title,
        String content,
        String deepLink
) {}
