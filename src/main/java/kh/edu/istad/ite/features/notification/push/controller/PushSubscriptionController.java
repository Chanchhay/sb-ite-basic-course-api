package kh.edu.istad.ite.features.notification.push.controller;

import jakarta.validation.Valid;
import kh.edu.istad.ite.config.props.PushProps;
import kh.edu.istad.ite.features.notification.push.dto.PushSubscriptionResponse;
import kh.edu.istad.ite.features.notification.push.dto.SubscribePushRequest;
import kh.edu.istad.ite.features.notification.push.dto.UnsubscribePushRequest;
import kh.edu.istad.ite.features.notification.push.service.PushSubscriptionService;
import kh.edu.istad.ite.shared.helper.AuthHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;
    private final PushProps pushProps;

    @PostMapping("/push-subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@Valid @RequestBody SubscribePushRequest request) {
        pushSubscriptionService.subscribe(AuthHelper.currentUserId(), request);
    }

    @DeleteMapping("/push-subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@Valid @RequestBody UnsubscribePushRequest request) {
        pushSubscriptionService.unsubscribe(AuthHelper.currentUserId(), request.endpoint());
    }

    @PostMapping("/internal/push-subscriptions/lookup")
    public List<PushSubscriptionResponse> findForUsers(
            @RequestHeader("X-Push-Secret") String providedSecret,
            @RequestBody List<UUID> userIds
    ) {
        requireInternalSecret(providedSecret);
        return pushSubscriptionService.findForUsers(userIds);
    }

    @DeleteMapping("/internal/push-subscriptions/by-endpoint")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteByEndpoint(
            @RequestHeader("X-Push-Secret") String providedSecret,
            @RequestParam String endpoint
    ) {
        requireInternalSecret(providedSecret);
        pushSubscriptionService.deleteByEndpoint(endpoint);
    }

    private void requireInternalSecret(String providedSecret) {
        String configured = pushProps.getInternalSecret();

        if (!StringUtils.hasText(configured) || !configured.equals(providedSecret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid push secret.");
        }
    }
}
