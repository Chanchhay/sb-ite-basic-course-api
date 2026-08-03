package kh.edu.istad.ite.features.notification.dto;

import jakarta.validation.constraints.NotBlank;
import kh.edu.istad.ite.config.filter.SearchRequestDto;
import kh.edu.istad.ite.features.notification.entity.NotificationType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Pageable;

@Getter
@Setter
public class NotificationSearchRequest extends SearchRequestDto {
    @NotBlank
    private String receiverId;
    private NotificationType type;   // optional
    private Boolean isRead;          // optional
}
