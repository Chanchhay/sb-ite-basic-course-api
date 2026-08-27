package kh.edu.istad.ite.features.social.facebook;

import com.fasterxml.jackson.databind.JsonNode;
import kh.edu.istad.ite.config.props.FacebookProps;
import kh.edu.istad.ite.config.props.StorefrontProps;
import kh.edu.istad.ite.features.social.entity.BotSession;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/social/facebook/webhook", "/api/webhook"})
@RequiredArgsConstructor
@Slf4j
public class FacebookWebhookController {

    private final FacebookProps facebookProps;
    private final BusinessFacebookPageService pageService;
    private final FacebookCatalogService catalogService;
    private final FacebookCustomerService customerService;
    private final FacebookCartService cartService;
    private final FacebookCheckoutService checkoutService;
    private final FacebookGraphClient graphClient;
    private final StorefrontProps storefrontProps;


    @GetMapping(produces = org.springframework.http.MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.challenge", required = false) String challenge,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken) {
            
        log.info("Received Facebook Webhook Verification: mode={}, verifyToken={}, challenge={}", mode, verifyToken, challenge);

        String configuredToken = facebookProps.getWebhookVerifyToken();
        String receivedToken = verifyToken != null ? verifyToken.trim() : "";
        boolean matchesConfigured = configuredToken != null && configuredToken.trim().equals(receivedToken);
        boolean matchesFallback = "fluxibiz_verify_token".equals(receivedToken);

        if ("subscribe".equals(mode) && (matchesConfigured || matchesFallback)) {
            log.info("Facebook Webhook Verified Successfully. Returning challenge: {}", challenge);
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                    .body(challenge);
        }
        
        log.warn("Facebook Webhook Verification Failed: expected={}, got={}", configuredToken, verifyToken);
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

        // Shopping happens entirely inside the Mini App webview now — the
        // bot's only job in a text conversation is to point back at it,
        // never to browse/search the catalog through chat itself.
        sendOpenShopPrompt(page, senderId);
    }

    /** Messenger's equivalent of Telegram's "Open Shop" prompt — a single
     * web_url button into the same storefront webview the persistent menu
     * already offers, so a customer who just types something still lands
     * back in the Mini App rather than a text-based catalog flow. */
    private void sendOpenShopPrompt(BusinessFacebookPage page, String psid) {
        String miniAppUrl = storefrontProps.buildMessengerMiniAppUrl(page.getBusiness().getSlug());

        Map<String, Object> shopButton = new java.util.HashMap<>();
        shopButton.put("type", "web_url");
        shopButton.put("url", miniAppUrl);
        shopButton.put("title", "🛍 បើកហាង");
        shopButton.put("webview_height_ratio", "tall");
        shopButton.put("messenger_extensions", true);

        graphClient.sendButtonTemplate(page.getPageId(), page.getPageAccessTokenEncrypted(), psid,
                "👋 សូមស្វាគមន៍មកកាន់ " + page.getBusiness().getDisplayName() + "! ចុចប៊ូតុងខាងក្រោមដើម្បីទិញទំនិញ៖",
                List.of(shopButton));
    }

    private void handlePostback(BusinessFacebookPage page, String psid, String payload) {
        if (payload == null) return;

        // Auto-register/fetch customer and bot session for any postback
        BotSession session = customerService.getOrCreateSession(page, psid);

        if (payload.startsWith("ITEM:")) {
            try {
                UUID itemId = UUID.fromString(payload.substring("ITEM:".length()));
                catalogService.showItemDetail(page, psid, itemId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid ITEM postback payload: {}", payload);
            }
            return;
        }

        if (payload.startsWith("CART_ADD:")) {
            try {
                UUID itemId = UUID.fromString(payload.substring("CART_ADD:".length()));
                cartService.handleAddToCart(page, session, psid, itemId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid CART_ADD payload: {}", payload);
            }
            return;
        }

        if ("CART_VIEW".equals(payload)) {
            cartService.handleViewCart(page, session, psid);
            return;
        }

        if ("CART_CHECKOUT".equals(payload)) {
            checkoutService.promptPaymentMethod(page, psid);
            return;
        }

        if ("CHECKOUT_KHQR".equals(payload)) {
            checkoutService.handleCheckout(page, session, psid);
            return;
        }

        if ("CHECKOUT_PAY_LATER".equals(payload)) {
            checkoutService.handlePayLaterCheckout(page, session, psid);
            return;
        }

        if (payload.startsWith("ORDER_CANCEL:")) {
            try {
                UUID orderId = UUID.fromString(payload.substring("ORDER_CANCEL:".length()));
                checkoutService.cancelCheckout(page.getBusiness().getId(), orderId);
                graphClient.sendTextMessage(page.getPageId(), page.getPageAccessTokenEncrypted(), psid, "✅ ការបញ្ជាទិញត្រូវបានលុបចោល។");
                cartService.handleViewCart(page, session, psid);
            } catch (Exception e) {
                log.warn("Invalid ORDER_CANCEL payload or error: {}", e.getMessage());
            }
            return;
        }

        if (payload.startsWith("CART_INC:")) {
            try {
                UUID itemId = UUID.fromString(payload.substring("CART_INC:".length()));
                cartService.handleIncrementCartItem(page, session, psid, itemId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid CART_INC payload: {}", payload);
            }
            return;
        }

        if (payload.startsWith("CART_DEC:")) {
            try {
                UUID itemId = UUID.fromString(payload.substring("CART_DEC:".length()));
                cartService.handleDecrementCartItem(page, session, psid, itemId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid CART_DEC payload: {}", payload);
            }
            return;
        }

        if (payload.startsWith("CART_RM:")) {
            try {
                UUID itemId = UUID.fromString(payload.substring("CART_RM:".length()));
                cartService.handleRemoveCartItem(page, session, psid, itemId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid CART_RM payload: {}", payload);
            }
            return;
        }

        if ("ORDER_HISTORY".equals(payload)) {
            customerService.handleOrderHistory(page, session, psid);
            return;
        }

        if ("CATALOG_CATEGORIES".equals(payload)) {
            catalogService.showCategories(page, psid);
            return;
        }

        if (payload.startsWith("CATALOG_CAT:")) {
            try {
                UUID categoryId = UUID.fromString(payload.substring("CATALOG_CAT:".length()));
                catalogService.showCatalogByCategory(page, psid, categoryId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid CATALOG_CAT payload: {}", payload);
            }
            return;
        }

        if ("GET_STARTED".equals(payload)) {
            sendOpenShopPrompt(page, psid);
        }
    }
}
