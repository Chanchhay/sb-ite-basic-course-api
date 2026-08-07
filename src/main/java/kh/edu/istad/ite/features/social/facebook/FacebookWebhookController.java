package kh.edu.istad.ite.features.social.facebook;

import com.fasterxml.jackson.databind.JsonNode;
import kh.edu.istad.ite.config.props.FacebookProps;
import kh.edu.istad.ite.features.social.entity.BusinessFacebookPage;
import kh.edu.istad.ite.features.social.service.BusinessFacebookPageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/social/facebook/webhook")
@RequiredArgsConstructor
@Slf4j
public class FacebookWebhookController {

    private final FacebookProps facebookProps;
    private final BusinessFacebookPageService pageService;
    private final FacebookCatalogService catalogService;


    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.challenge", required = false) String challenge,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken) {
            
        log.info("Received Facebook Webhook Verification: mode={}, verifyToken={}", mode, verifyToken);

        if ("subscribe".equals(mode) && facebookProps.getWebhookVerifyToken().equals(verifyToken)) {
            log.info("Facebook Webhook Verified Successfully");
            return ResponseEntity.ok(challenge);
        }
        
        log.warn("Facebook Webhook Verification Failed");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }


    @PostMapping
    public ResponseEntity<Void> receiveEvent(@RequestBody String payloadString) {
        log.info("Received Facebook Webhook Event payload");
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode payload = mapper.readTree(payloadString);
            
            if (payload.has("object") && "page".equals(payload.get("object").asText())) {
                JsonNode entryNode = payload.get("entry");
                if (entryNode != null && entryNode.isArray()) {
                    for (JsonNode entry : entryNode) {
                        JsonNode messagingNode = entry.get("messaging");
                        if (messagingNode != null && messagingNode.isArray()) {
                            for (JsonNode messaging : messagingNode) {
                                handleMessagingEvent(messaging);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing Facebook Webhook Event", e);
        }

        return ResponseEntity.ok().build();
    }


    @PostMapping("/setup")
    public ResponseEntity<String> setupPage(
            @RequestParam UUID businessId,
            @RequestParam String pageId,
            @RequestParam String pageName,
            @RequestParam String pageAccessToken) {
        
        pageService.registerPage(businessId, pageId, pageName, pageAccessToken);
        return ResponseEntity.ok("Page registered successfully for Auto-Reply testing!");
    }

    private void handleMessagingEvent(JsonNode messaging) {
        String senderId = messaging.path("sender").path("id").asText();
        String pageId = messaging.path("recipient").path("id").asText();

        Optional<BusinessFacebookPage> optPage = pageService.findByPageId(pageId);
        if (optPage.isEmpty() || !Boolean.TRUE.equals(optPage.get().getIsActive())) {
            log.warn("Page ID {} not found in database or is inactive.", pageId);
            return;
        }
        BusinessFacebookPage page = optPage.get();

        if (messaging.has("postback")) {
            String payload = messaging.path("postback").path("payload").asText();
            log.info("🔘 Postback from PSID [{}] to Page [{}]: {}", senderId, pageId, payload);
            handlePostback(page, senderId, payload);
            return;
        }

        if (!messaging.has("message")) return; // Ignore other events (delivery, read receipts, etc.)

        JsonNode messageNode = messaging.get("message");

        if (messageNode.has("quick_reply")) {
            handlePostback(page, senderId, messageNode.path("quick_reply").path("payload").asText());
            return;
        }

        String text = messageNode.path("text").asText();
        log.info("📩 Message from PSID [{}] to Page [{}]: {}", senderId, pageId, text);

        if (isCatalogCommand(text)) {
            catalogService.showCatalog(page, senderId);
        } else {

            catalogService.sendWelcomeMenu(page, senderId);
        }
    }

    private void handlePostback(BusinessFacebookPage page, String psid, String payload) {
        if (payload == null) return;

        if (payload.startsWith("ITEM:")) {
            try {
                UUID itemId = UUID.fromString(payload.substring("ITEM:".length()));
                catalogService.showItemDetail(page, psid, itemId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid ITEM postback payload: {}", payload);
            }
            return;
        }

        if ("CATALOG".equals(payload)) {
            catalogService.showCatalog(page, psid);
            return;
        }

        if ("GET_STARTED".equals(payload)) {
            catalogService.sendWelcomeMenu(page, psid);
        }
    }

    private boolean isCatalogCommand(String text) {
        if (text == null) return false;
        String normalized = text.trim().toLowerCase();
        return normalized.equals("catalog") || normalized.equals("menu")
                || normalized.equals("ម៉ឺនុយ") || normalized.equals("ផលិតផល");
    }
}
