package kh.edu.istad.ite.features.social.service;

import kh.edu.istad.ite.config.props.StorefrontProps;
import kh.edu.istad.ite.config.security.CredentialCipher;
import kh.edu.istad.ite.features.auth.AuthService;
import kh.edu.istad.ite.features.auth.dto.RegisterRequest;
import kh.edu.istad.ite.features.auth.dto.RoleEnum;
import kh.edu.istad.ite.features.cart.entity.Cart;
import kh.edu.istad.ite.features.cart.entity.CartItem;
import kh.edu.istad.ite.features.cart.repository.CartItemRepository;
import kh.edu.istad.ite.features.cart.repository.CartRepository;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemGroup;
import kh.edu.istad.ite.features.catalog.entity.ItemImage;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.catalog.repository.ItemGroupRepository;
import kh.edu.istad.ite.features.catalog.repository.ItemVariantRepository;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.minio.MinioService;
import kh.edu.istad.ite.features.customer.entity.Customer;
import kh.edu.istad.ite.features.customer.entity.CustomerChannelIdentity;
import kh.edu.istad.ite.features.customer.entity.GlobalCustomer;
import kh.edu.istad.ite.features.customer.repository.CustomerChannelIdentityRepository;
import kh.edu.istad.ite.features.customer.service.CustomerIdentityService;
import kh.edu.istad.ite.features.social.entity.BotSession;
import kh.edu.istad.ite.features.social.entity.BusinessTelegramBot;
import kh.edu.istad.ite.features.social.repository.BotSessionRepository;
import kh.edu.istad.ite.features.social.repository.BusinessTelegramBotRepository;
import kh.edu.istad.ite.features.social.telegram.InlineKeyboardButton;
import kh.edu.istad.ite.features.social.telegram.TelegramBotClient;
import kh.edu.istad.ite.features.social.telegram.TelegramCallbackQuery;
import kh.edu.istad.ite.features.social.telegram.TelegramKeyboards;
import kh.edu.istad.ite.features.social.telegram.TelegramUIHelper;
import kh.edu.istad.ite.features.social.telegram.TelegramFrom;
import kh.edu.istad.ite.features.social.telegram.TelegramUpdate;
import kh.edu.istad.ite.shared.enums.BusinessFeature;
import kh.edu.istad.ite.shared.enums.CartStatus;
import kh.edu.istad.ite.shared.enums.ChannelType;
import kh.edu.istad.ite.shared.enums.ItemStatus;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import kh.edu.istad.ite.features.catalog.specification.ItemSpecifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import kh.edu.istad.ite.shared.enums.OrderChannel;

import kh.edu.istad.ite.features.cart.entity.CartItemSelection;
import kh.edu.istad.ite.features.catalog.entity.ItemColor;
import kh.edu.istad.ite.features.catalog.entity.ItemAttribute;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramWebhookServiceImpl implements TelegramWebhookService {

    private static final String STATE_IDLE = "IDLE";

    private static final String STATE_REGISTER_USERNAME = "REGISTER_USERNAME";
    private static final String STATE_REGISTER_PASSWORD = "REGISTER_PASSWORD";
    private static final String STATE_REGISTER_NAME = "REGISTER_NAME";
    private static final String STATE_REGISTER_EMAIL = "REGISTER_EMAIL";
    private static final String STATE_REGISTER_PHONE = "REGISTER_PHONE";

    private static final String STATE_LOGIN_EMAIL = "LOGIN_EMAIL";
    private static final String STATE_LOGIN_PASSWORD = "LOGIN_PASSWORD";

    private static final String STATE_SEARCH_AWAITING_KEYWORD = "SEARCH_AWAITING_KEYWORD";

    private static final Set<String> REQUIRES_REGISTRATION = Set.of(
            "menu:cart", "menu:checkout", "menu:orders", "menu:profile", "menu:history");

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9 ]{8,30}$");

    private static final int CATALOG_PAGE_SIZE = 5;
    private static final String CATALOG_TOKEN_ALL = "all";

    private final BusinessTelegramBotRepository telegramBotRepository;
    private final BotSessionRepository botSessionRepository;
    private final CustomerChannelIdentityRepository customerChannelIdentityRepository;
    private final CustomerIdentityService customerIdentityService;
    private final ItemRepository itemRepository;
    private final ItemGroupRepository itemGroupRepository;
    private final ItemVariantRepository itemVariantRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CredentialCipher credentialCipher;
    private final TelegramBotClient telegramBotClient;
    private final BusinessHelper businessHelper;
    private final TelegramUIHelper uiHelper;
    private final AuthService authService;
    private final TelegramStockHelper stockHelper;
    private final KeycloakBotAuthService keycloakAuthService;
    private final TelegramCheckoutService telegramCheckoutService;
    private final TelegramCustomerScreenService screenService;
    private final MinioService minioService;
    private final kh.edu.istad.ite.features.discount.service.DiscountService discountService;
    private final StorefrontProps storefrontProps;

    @Override
    @Transactional
    public void handleUpdate(String webhookSecret, String secretTokenHeader, TelegramUpdate update) {
        try {
            BusinessTelegramBot setting = telegramBotRepository.findByWebhookSecret(webhookSecret).orElse(null);

            if (setting == null) {
                log.warn("Rejected Telegram webhook call: unknown webhookSecret in path");
                return;
            }

            if (secretTokenHeader == null || !secretTokenHeader.equals(setting.getWebhookSecret())) {
                log.warn("Rejected Telegram webhook call: secret_token header mismatch for business {}",
                        setting.getBusiness().getId());
                return;
            }

            if (!businessHelper.isFeatureEnabled(setting.getBusiness().getId(), BusinessFeature.TELEGRAM_BOT)) {
                log.info("Ignoring Telegram update: the platform disabled the bot for business {}",
                        setting.getBusiness().getId());
                return;
            }

            // These two dashboard toggles are independent — a business can
            // run the old text/reply-keyboard flow only, Mini App only, or
            // both at once (the button is offered *in addition to* the text
            // flow rather than replacing it when both are on).
            boolean textFlowEnabled = Boolean.TRUE.equals(setting.getIsActive());
            boolean miniAppEnabled = Boolean.TRUE.equals(setting.getIsMiniAppEnabled());

            if (!textFlowEnabled && !miniAppEnabled) {
                log.info("Ignoring Telegram update: bot fully disabled for business {}", setting.getBusiness().getId());
                return;
            }

            TelegramCallbackQuery callbackQuery = update == null ? null : update.callbackQuery();
            Long chatId = resolveChatId(update);

            if (chatId == null) {
                return;
            }

            String botToken = credentialCipher.decrypt(setting.getBotTokenEncrypted());

            if (isGroupChat(update)) {
                // The persistent menu button (set via setChatMenuButton) is a
                // private-chat-only affordance — it never appears in groups,
                // and the text/reply-keyboard flow below was never designed
                // for a shared chat either. A web_app inline button on a
                // reply is the only way to offer "Open Shop" there, and it
                // opens with the identity of whichever member taps it, same
                // as private chat — no shared/group-wide session involved.
                if (miniAppEnabled) {
                    sendOpenShopPrompt(botToken, chatId, setting);
                }
                return;
            }

            if (miniAppEnabled) {
                sendOpenShopPrompt(botToken, chatId, setting);
            }

            if (!textFlowEnabled) {
                // Mini-App-only private chat: the button above (if any) is
                // the whole interaction — nothing left to run.
                return;
            }

            BotSession session = findOrCreateSession(setting, String.valueOf(chatId));
            if (session.getContext() == null) {
                session.setContext(new HashMap<>());
            }

            attachExistingCustomerIfAny(setting, session);

            if (callbackQuery != null) {
                // Cosmetic only - never make the customer wait on the spinner.
                telegramBotClient.answerCallbackQueryAsync(botToken, callbackQuery.id(), null);
                Integer messageId = null;
                if (callbackQuery.message() != null) {
                    messageId = callbackQuery.message().messageId();
                }
                handleCallback(update, botToken, chatId, messageId, callbackQuery.data(), setting, session);
            } else if (update.message() != null && update.message().contact() != null) {
                handleContactShared(botToken, chatId, update.message().contact(), setting, session);
            } else {
                String text = update.message().text() == null ? "" : update.message().text().trim();
                handleText(botToken, chatId, text, setting, session);
            }

            session.setUpdatedAt(LocalDateTime.now());
            botSessionRepository.save(session);
        } catch (Exception e) {
            log.error("Fatal error during Telegram webhook processing: {}", e.getMessage(), e);

            try {
                Long failedChatId = resolveChatId(update);
                BusinessTelegramBot failedSetting = telegramBotRepository.findByWebhookSecret(webhookSecret)
                        .orElse(null);

                if (failedChatId != null && failedSetting != null) {
                    String token = credentialCipher.decrypt(failedSetting.getBotTokenEncrypted());
                    telegramBotClient.sendMessage(token, failedChatId,
                            "❌ មានបញ្ហាបច្ចេកទេសបណ្ដោះអាសន្ន។ សូមព្យាយាមម្ដងទៀត ឬវាយ /start ដើម្បីចាប់ផ្ដើមឡើងវិញ។");
                }
            } catch (Exception ignored) {
                log.warn("Could not deliver the failure notice to the customer");
            }
        }
    }

    private void handleContactShared(String botToken, Long chatId, kh.edu.istad.ite.features.social.telegram.TelegramContact contact, BusinessTelegramBot setting, BotSession session) {
        if (contact == null || !StringUtils.hasText(contact.phoneNumber())) return;
        String phone = contact.phoneNumber();
        if (session.getCustomer() != null && session.getCustomer().getGlobalCustomer() != null) {
            GlobalCustomer gc = session.getCustomer().getGlobalCustomer();
            gc.setPhoneNumber(phone);
            customerIdentityService.resolve(gc.getKeycloakUserId(), gc.getEmail(), phone, gc.getFullName());
            telegramBotClient.sendMessage(botToken, chatId, "✅ ទទួលបានលេខទូរស័ព្ទ `" + phone + "` ដោយជោគជ័យ!", TelegramKeyboards.persistentReplyMenu());
            showProfile(botToken, chatId, session);
        }
    }

    private Long resolveChatId(TelegramUpdate update) {
        if (update == null)
            return null;
        if (update.callbackQuery() != null && update.callbackQuery().message() != null
                && update.callbackQuery().message().chat() != null) {
            return update.callbackQuery().message().chat().id();
        }
        if (update.message() != null && update.message().chat() != null) {
            return update.message().chat().id();
        }
        return null;
    }

    private boolean isGroupChat(TelegramUpdate update) {
        String chatType = null;
        if (update.callbackQuery() != null && update.callbackQuery().message() != null
                && update.callbackQuery().message().chat() != null) {
            chatType = update.callbackQuery().message().chat().type();
        } else if (update.message() != null && update.message().chat() != null) {
            chatType = update.message().chat().type();
        }
        return "group".equals(chatType) || "supergroup".equals(chatType);
    }

    private void sendOpenShopPrompt(String botToken, Long chatId, BusinessTelegramBot setting) {
        if (!Boolean.TRUE.equals(setting.getIsMiniAppEnabled())) {
            // Mini App isn't turned on for this business — nothing to open.
            // For a group this means staying quiet rather than pointing
            // members at an unavailable feature; the private-chat call site
            // never reaches here with it off (checked before calling).
            return;
        }

        String miniAppUrl = storefrontProps.buildMiniAppUrl(setting.getBusiness().getSlug());
        telegramBotClient.sendMessage(
                botToken,
                chatId,
                "🛍 Tap below to open " + setting.getBusiness().getDisplayName() + "'s shop.",
                List.of(List.of(InlineKeyboardButton.webApp("🛍 Open Shop", miniAppUrl))));
    }

    private BotSession findOrCreateSession(BusinessTelegramBot setting, String chatId) {
        return botSessionRepository
                .findByBusiness_IdAndChannelAndExternalId(setting.getBusiness().getId(), ChannelType.TELEGRAM, chatId)
                .orElseGet(() -> {
                    BotSession created = new BotSession();
                    created.setBusiness(setting.getBusiness());
                    created.setChannel(ChannelType.TELEGRAM);
                    created.setExternalId(chatId);
                    created.setState(STATE_IDLE);
                    created.setContext(new HashMap<>());
                    return botSessionRepository.save(created);
                });
    }

    private void attachExistingCustomerIfAny(BusinessTelegramBot setting, BotSession session) {
        if (session.getCustomer() != null)
            return;
        try {
            customerChannelIdentityRepository
                    .findByBusiness_IdAndChannelAndExternalId(
                            setting.getBusiness().getId(), ChannelType.TELEGRAM, session.getExternalId())
                    .ifPresent(identity -> session.setCustomer(identity.getCustomer()));
        } catch (Exception e) {
            log.warn("Could not attach existing customer: {}", e.getMessage());
        }
    }

    private void autoAuthenticateTelegramUser(TelegramUpdate update, BusinessTelegramBot setting, BotSession session) {
        if (update == null) {
            return;
        }

        TelegramFrom from = null;
        String phoneNumber = null;

        if (update.message() != null) {
            if (update.message().from() != null) {
                from = update.message().from();
            }
            if (update.message().contact() != null && update.message().contact().phoneNumber() != null) {
                phoneNumber = update.message().contact().phoneNumber();
            }
        } else if (update.callbackQuery() != null && update.callbackQuery().from() != null) {
            from = update.callbackQuery().from();
        }

        if (from == null || from.id() == null) {
            return;
        }

        try {
            Long tgId = from.id();
            String firstName = from.firstName();
            String lastName = from.lastName();
            String username = from.username();

            if (username != null) {
                session.getContext().put("tg_username", username);
            }

            String fullName = (firstName != null ? firstName : "") + (lastName != null && !lastName.isBlank() ? " " + lastName : "");
            if (!StringUtils.hasText(fullName.trim())) {
                fullName = username != null ? username : "Telegram User";
            }

            KeycloakBotAuthService.KeycloakUserInfo userInfo = keycloakAuthService
                    .findOrCreateTelegramKeycloakUser(tgId, firstName, lastName, username, phoneNumber);

            GlobalCustomer globalCustomer = customerIdentityService.resolve(
                    CustomerIdentityService.parseKeycloakId(userInfo.id()),
                    userInfo.email(),
                    phoneNumber != null ? phoneNumber : userInfo.phoneNumber(),
                    fullName);

            Customer customer = findOrCreateCustomer(setting, globalCustomer);
            linkTelegramIdentity(setting, session, customer);

            session.setCustomer(customer);
            log.info("Auto-registered/synced Telegram user {} (@{}) into Keycloak & attached to session", tgId, username);
        } catch (Exception e) {
            log.error("Failed to auto-authenticate Telegram user {}: {}", from.id(), e.getMessage(), e);
        }
    }

    private void handleText(String botToken, Long chatId, String text, BusinessTelegramBot setting,
            BotSession session) {
        String state = session.getState() == null ? STATE_IDLE : session.getState();

        switch (state) {
            case STATE_REGISTER_USERNAME -> handleRegisterUsername(botToken, chatId, text, session);
            case STATE_REGISTER_PASSWORD -> handleRegisterPassword(botToken, chatId, text, session);
            case STATE_REGISTER_NAME -> handleRegisterName(botToken, chatId, text, session);
            case STATE_REGISTER_EMAIL -> handleRegisterEmail(botToken, chatId, text, session);
            case STATE_REGISTER_PHONE -> handleRegisterPhone(botToken, chatId, text, session);

            case STATE_LOGIN_EMAIL -> handleLoginEmail(botToken, chatId, text, session);
            case STATE_LOGIN_PASSWORD -> handleLoginPasswordAndKeycloak(botToken, chatId, text, setting, session);

            case STATE_SEARCH_AWAITING_KEYWORD -> handleSearchKeyword(botToken, chatId, text, setting, session);
            default -> handleIdleText(botToken, chatId, text, setting, session);
        }
    }

    private void handleIdleText(String botToken, Long chatId, String text, BusinessTelegramBot setting,
            BotSession session) {
        if ("/start".equalsIgnoreCase(text)) {
            sendMainMenu(botToken, chatId, session, setting);
            return;
        }
        if ("/help".equalsIgnoreCase(text) || text.contains("Help") || text.contains("ជំនួយ")) {
            telegramBotClient.sendMessage(botToken, chatId,
                    "🤖 *ពាក្យបញ្ជាដែលមាន៖*\n/start - បើកម៉ឺនុយដើម\n/help - មើលជំនួយ\n/catalog - មើលបញ្ជីទំនិញ\n/cart - មើលកន្ត្រក", TelegramKeyboards.persistentReplyMenu());
            return;
        }
        if ("/getid".equalsIgnoreCase(text) || "/myid".equalsIgnoreCase(text)) {
            telegramBotClient.sendMessage(botToken, chatId,
                    "🔑 *Chat ID របស់អ្នកគឺ៖* `" + chatId + "`\n\nសូមចម្លងលេខខាងលើនេះយកទៅបញ្ចូលក្នុងកន្លែងកំណត់ Setting របស់ហាងអ្នក។");
            return;
        }

        if (text.contains("ផលិតផល") || text.contains("Catalog") || "/catalog".equalsIgnoreCase(text)) {
            showCategories(botToken, chatId, setting, session);
            return;
        }
        if (text.contains("កន្ត្រក") || text.contains("Cart") || "/cart".equalsIgnoreCase(text)) {
            showCart(botToken, chatId, setting, session);
            return;
        }
        if (text.contains("ប្រវត្តិ") || text.contains("Orders") || text.contains("History") || "/orders".equalsIgnoreCase(text)) {
            showActiveOrders(botToken, chatId, setting, session, 0);
            return;
        }
        if (text.contains("គណនី") || text.contains("Profile") || "/profile".equalsIgnoreCase(text)) {
            showProfile(botToken, chatId, session);
            return;
        }

        sendMainMenu(botToken, chatId, session, setting);
    }

    private void sendMainMenu(String botToken, Long chatId, BotSession session, BusinessTelegramBot setting) {
        boolean registered = session.getCustomer() != null;
        String customerName = registered && session.getCustomer().getGlobalCustomer() != null 
                ? session.getCustomer().getGlobalCustomer().getFullName() 
                : null;
        String welcomeText = uiHelper.renderWelcomeMessage(setting, customerName);
        telegramBotClient.sendMessage(botToken, chatId, welcomeText, TelegramKeyboards.mainMenu(registered), TelegramKeyboards.persistentReplyMenu());
    }

    private void handleCallback(TelegramUpdate update, String botToken, Long chatId, Integer messageId, String data,
            BusinessTelegramBot setting, BotSession session) {
        if (data == null)
            return;

        if (data.equals("menu:catalog") || data.equals("catback")) {
            showCategories(botToken, chatId, setting, session);
            return;
        }
        if (data.startsWith("cat:")) {
            showItemsPage(botToken, chatId, setting, session, data.substring("cat:".length()), 0);
            return;
        }
        if (data.equals("catpage:next") || data.equals("catpage:prev")) {
            handleCatalogPaging(botToken, chatId, setting, session, data.equals("catpage:next"));
            return;
        }
        if (data.startsWith("item:")) {
            showItemDetail(botToken, chatId, setting, session, data.substring("item:".length()));
            return;
        }
        if (data.equals("itemback")) {
            showStoredItemsPage(botToken, chatId, setting, session);
            return;
        }
        if (data.startsWith("cart:pickvariant:")) {
            showVariantPicker(botToken, chatId, setting, data.substring("cart:pickvariant:".length()));
            return;
        }
        if (data.startsWith("cart:addv:")) {
            handleAddVariantToCart(botToken, chatId, setting, session, data.substring("cart:addv:".length()));
            return;
        }
        if (data.startsWith("cart:addcolor:")) {
            handleAddColorToCart(botToken, chatId, setting, session, data.substring("cart:addcolor:".length()));
            return;
        }
        if (data.startsWith("cart:addattr:")) {
            handleAddAttrToCart(botToken, chatId, setting, session, data.substring("cart:addattr:".length()));
            return;
        }
        if (data.startsWith("cart:add:")) {
            handleAddToCart(botToken, chatId, setting, session, data.substring("cart:add:".length()));
            return;
        }
        if (data.startsWith("cart:plus:")) {
            updateCartItemQty(botToken, chatId, setting, session, data.substring("cart:plus:".length()), 1);
            return;
        }
        if (data.startsWith("cart:minus:")) {
            updateCartItemQty(botToken, chatId, setting, session, data.substring("cart:minus:".length()), -1);
            return;
        }
        if (data.startsWith("cart:rm:")) {
            removeCartItem(botToken, chatId, setting, session, data.substring("cart:rm:".length()));
            return;
        }

        if (data.equals("auth:sharephone")) {
            telegramBotClient.sendMessage(botToken, chatId,
                    uiHelper.header("📱", "ចែករំលែកលេខទូរស័ព្ទ (SHARE PHONE NUMBER)")
                            + "📲 សូមចុចប៊ូតុង **\"📱 ចែករំលែកលេខទូរស័ព្ទ (Share Phone Number)\"** ខាងក្រោម ដើម្បីផ្ញើលេខទូរស័ព្ទរបស់អ្នកមកកាន់ប្រព័ន្ធ ៖",
                    TelegramKeyboards.shareContactReplyKeyboard());
            return;
        }

        if (data.equals("search:cancel") || data.equals("auth:cancel")) {
            session.setState(STATE_IDLE);
            telegramBotClient.sendMessage(botToken, chatId, "❌ បានបោះបង់ប្រតិបត្តិការ។",
                    TelegramKeyboards.backToMenu());
            return;
        }

        if (data.startsWith("auth:reg:gender:")) {
            String gender = data.substring("auth:reg:gender:".length());
            executeRealRegistration(botToken, chatId, gender, setting, session);
            return;
        }

        if (data.startsWith("orders:page:")) {
            showActiveOrders(botToken, chatId, setting, session, parsePage(data.substring("orders:page:".length())));
            return;
        }
        if (data.startsWith("history:page:")) {
            showOrderHistory(botToken, chatId, setting, session, parsePage(data.substring("history:page:".length())));
            return;
        }
        if (data.startsWith("order:view:")) {
            showOrderDetail(botToken, chatId, setting, session, data.substring("order:view:".length()));
            return;
        }
        if (data.startsWith("order:cancel:")) {
            handleOrderCancel(botToken, chatId, setting, data.substring("order:cancel:".length()));
            return;
        }

        switch (data) {
            case "menu:main" -> sendMainMenu(botToken, chatId, session, setting);
            case "auth:signin", "auth:register:start", "auth:login:start" -> handleSignIn(update, botToken, chatId, setting, session);
            case "auth:logout" -> handleLogout(botToken, chatId, setting, session);
            case "menu:profile" -> showProfile(botToken, chatId, session);
            case "menu:search" -> startSearch(botToken, chatId, session);
            case "menu:cart" -> showCart(botToken, chatId, setting, session);
            case "menu:checkout" -> handleCheckout(botToken, chatId, setting, session);

            case "menu:orders" -> showActiveOrders(botToken, chatId, setting, session, 0);
            case "menu:history" -> showOrderHistory(botToken, chatId, setting, session, 0);
            case "menu:location" -> showLocation(botToken, chatId, setting);

            default -> telegramBotClient.sendMessage(botToken, chatId, "សូមអភ័យទោស ខ្ញុំមិនយល់ពាក្យបញ្ជានេះទេ។",
                    TelegramKeyboards.backToMenu());
        }
    }

    private void handleCheckout(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session) {
        if (requireLogin(botToken, chatId, session)) {
            return;
        }

        TelegramCheckoutService.CheckoutDraft draft;
        try {
            draft = telegramCheckoutService.createCheckout(
                    setting.getBusiness().getId(),
                    session.getCustomer().getId());
        } catch (TelegramCheckoutException exception) {
            // សារនេះសរសេរជាភាសាខ្មែររួចហើយ សម្រាប់បង្ហាញដល់អតិថិជនផ្ទាល់។
            telegramBotClient.sendMessage(botToken, chatId, exception.getMessage(),
                    List.of(List.of(new InlineKeyboardButton("🛍️ មើលបញ្ជីទំនិញ", "menu:catalog")),
                            List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main"))));
            return;
        }

        String caption = uiHelper.header("⚡️", "វិក្កយបត្រទូទាត់ (KHQR)")
                + "🏪 ហាង ៖ *" + setting.getBusiness().getDisplayName() + "*\n"
                + "🧾 លេខវិក្កយបត្រ ៖ `" + draft.invoiceNumber() + "`\n"
                + "📦 ចំនួនមុខទំនិញ ៖ `" + draft.itemCount() + " មុខ`\n"
                + "💳 *ទឹកប្រាក់ត្រូវបង់ ៖* " + uiHelper.formatPrice(draft.total(), setting) + "\n"
                + uiHelper.divider()
                + "📲 សូមស្កែន QR នេះជាមួយ App ធនាគារ\n"
                + "⚡️ ប្រព័ន្ធនឹងបញ្ជាក់ដោយ*ស្វ័យប្រវត្តិ*ក្នុងរយៈពេលពីរបីវិនាទី\n"
                + "⏰ កូដផុតកំណត់ក្នុងរយៈពេល *5 នាទី*";

        List<List<InlineKeyboardButton>> keyboard = List.of(
                List.of(new InlineKeyboardButton("❌ បោះបង់ការបញ្ជាទិញ", "order:cancel:" + draft.orderId())),
                List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main")));

        Integer messageId = telegramBotClient.sendPhotoBytes(botToken, chatId, draft.qrPng(),
                "khqr-" + draft.invoiceNumber() + ".png", caption, keyboard);

        if (messageId != null) {
            telegramCheckoutService.updateQrMessageId(draft.qrCodeId(), messageId);
        }
    }

    private void handleOrderCancel(String botToken, Long chatId,
            BusinessTelegramBot setting, String orderIdRaw) {
        try {
            telegramCheckoutService.cancelCheckout(
                    setting.getBusiness().getId(), UUID.fromString(orderIdRaw));
        } catch (Exception e) {
            log.warn("Could not cancel Telegram order {}: {}", orderIdRaw, e.getMessage());
        }

        telegramBotClient.sendMessage(botToken, chatId,
                "❌ ការបញ្ជាទិញត្រូវបានបោះបង់។ ទំនិញនៅតែរក្សាទុកក្នុងកន្ត្រករបស់អ្នក។",
                List.of(List.of(new InlineKeyboardButton("🛒 មើលកន្ត្រក", "menu:cart")),
                        List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main"))));
    }

    private void showActiveOrders(String botToken, Long chatId, BusinessTelegramBot setting,
            BotSession session, int page) {
        if (requireLogin(botToken, chatId, session)) {
            return;
        }
        TelegramCustomerScreenService.Screen screen = screenService.activeOrders(setting, session.getCustomer().getId(),
                page);
        telegramBotClient.sendMessage(botToken, chatId, screen.text(), screen.keyboard());
    }

    private void showOrderHistory(String botToken, Long chatId, BusinessTelegramBot setting,
            BotSession session, int page) {
        if (requireLogin(botToken, chatId, session)) {
            return;
        }
        TelegramCustomerScreenService.Screen screen = screenService.orderHistory(setting, session.getCustomer().getId(),
                page);
        telegramBotClient.sendMessage(botToken, chatId, screen.text(), screen.keyboard());
    }

    private void showOrderDetail(String botToken, Long chatId, BusinessTelegramBot setting,
            BotSession session, String raw) {
        if (requireLogin(botToken, chatId, session)) {
            return;
        }

        String[] parts = raw.split(":", 2);
        if (parts.length != 2) {
            telegramBotClient.sendMessage(botToken, chatId, "⚠️ ទិន្នន័យមិនត្រឹមត្រូវ។",
                    TelegramKeyboards.backToMenu());
            return;
        }

        String scope = "orders".equals(parts[0]) ? "orders" : "history";

        UUID orderId;
        try {
            orderId = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            telegramBotClient.sendMessage(botToken, chatId, "⚠️ លេខការបញ្ជាទិញមិនត្រឹមត្រូវ។",
                    TelegramKeyboards.backToMenu());
            return;
        }

        TelegramCustomerScreenService.Screen screen = screenService.orderDetail(setting, session.getCustomer().getId(),
                orderId, scope);
        telegramBotClient.sendMessage(botToken, chatId, screen.text(), screen.keyboard());
    }

    private void showLocation(String botToken, Long chatId, BusinessTelegramBot setting) {
        TelegramCustomerScreenService.Screen screen = screenService.storeLocation(setting);
        telegramBotClient.sendMessage(botToken, chatId, screen.text(), screen.keyboard());
    }

    private boolean requireLogin(String botToken, Long chatId, BotSession session) {
        if (session.getCustomer() != null) {
            return false;
        }
        telegramBotClient.sendMessage(botToken, chatId,
                "🔐 សូមចូលគណនីជាមុនសិន ដើម្បីប្រើប្រាស់មុខងារនេះ។",
                List.of(List.of(new InlineKeyboardButton("🔑 ចូលគណនី (Sign in)", "auth:signin"))));
        return true;
    }

    private void handleSignIn(TelegramUpdate update, String botToken, Long chatId, BusinessTelegramBot setting, BotSession session) {
        autoAuthenticateTelegramUser(update, setting, session);
        if (session.getCustomer() != null) {
            String name = session.getCustomer().getGlobalCustomer() != null ? session.getCustomer().getGlobalCustomer().getFullName() : "";
            telegramBotClient.sendMessage(botToken, chatId,
                    "🎉 *ចូលគណនីជាមួយ Telegram ជោគជ័យ!*\nសូមស្វាគមន៍ " + name + "!",
                    TelegramKeyboards.mainMenu(true));
        } else {
            telegramBotClient.sendMessage(botToken, chatId,
                    "❌ មិនអាចភ្ជាប់គណនី Telegram បានទេ។ សូមព្យាយាមម្ដងទៀត។",
                    TelegramKeyboards.mainMenu(false));
        }
    }

    private int parsePage(String raw) {
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void startLogin(String botToken, Long chatId, BotSession session) {
        if (session.getCustomer() != null) {
            telegramBotClient.sendMessage(botToken, chatId, "✅ អ្នកបានចូលគណនីរួចរាល់ហើយ។",
                    TelegramKeyboards.backToMenu());
            return;
        }
        session.setState(STATE_LOGIN_EMAIL);
        session.setContext(new HashMap<>());

        List<List<InlineKeyboardButton>> cancelBtn = List
                .of(List.of(new InlineKeyboardButton("❌ បោះបង់ (Cancel)", "auth:cancel")));
        String prompt = uiHelper.header("🔑", "ចូលគណនី (LOGIN)") +
                "សូមវាយ **អុីមែល (Email)** ឬ ឈ្មោះគណនីរបស់អ្នក ៖\n_(ឧទាហរណ៍៖ sengkim@gmail.com)_";
        telegramBotClient.sendMessage(botToken, chatId, prompt, cancelBtn);
    }

    private void handleLoginEmail(String botToken, Long chatId, String text, BotSession session) {
        if (!StringUtils.hasText(text)) {
            telegramBotClient.sendMessage(botToken, chatId, "⚠️ សូមវាយអុីមែល ឬ ឈ្មោះគណនីឲ្យបានត្រឹមត្រូវ៖");
            return;
        }
        session.getContext().put("login_email", text.trim());
        session.setState(STATE_LOGIN_PASSWORD);

        List<List<InlineKeyboardButton>> cancelBtn = List
                .of(List.of(new InlineKeyboardButton("❌ បោះបង់ (Cancel)", "auth:cancel")));
        String prompt = uiHelper.header("🔒", "បញ្ចូលពាក្យសម្ងាត់") +
                "សូមវាយ **ពាក្យសម្ងាត់ (Password)** សម្រាប់គណនី `" + text.trim() + "` ៖";
        telegramBotClient.sendMessage(botToken, chatId, prompt, cancelBtn);
    }

    private void handleLoginPasswordAndKeycloak(String botToken, Long chatId, String text, BusinessTelegramBot setting,
            BotSession session) {
        if (!StringUtils.hasText(text)) {
            telegramBotClient.sendMessage(botToken, chatId, "⚠️ សូមបញ្ចូលពាក្យសម្ងាត់៖");
            return;
        }

        String emailOrUsername = String.valueOf(session.getContext().get("login_email"));
        String password = text.trim();

        KeycloakBotAuthService.KeycloakUserInfo userInfo = keycloakAuthService.loginAndFetchUserInfo(emailOrUsername,
                password);
        if (userInfo == null) {
            telegramBotClient.sendMessage(botToken, chatId,
                    "❌ ចូលគណនីបរាជ័យ! អុីមែល ឬ ពាក្យសម្ងាត់របស់អ្នកមិនត្រឹមត្រូវទេ។",
                    TelegramKeyboards.backToMenu());
            session.setState(STATE_IDLE);
            return;
        }

        try {

            GlobalCustomer globalCustomer = customerIdentityService.resolve(
                    CustomerIdentityService.parseKeycloakId(userInfo.id()),
                    userInfo.email(),
                    userInfo.phoneNumber(),
                    userInfo.getFullName());

            Customer customer = findOrCreateCustomer(setting, globalCustomer);
            linkTelegramIdentity(setting, session, customer);

            session.setCustomer(customer);
            session.setState(STATE_IDLE);
            session.getContext().clear();

            String successMsg = uiHelper.header("🎉", "ចូលគណនីជោគជ័យ (LOGGED IN)") +
                    "ការផ្ទៀងផ្ទាត់ជាមួយ Keycloak Server ទទួលបានជោគជ័យ ១០០%!\n\n" +
                    "📛 ឈ្មោះពេញ ៖ *" + userInfo.getFullName() + "*\n" +
                    "👤 Username ៖ `" + userInfo.username() + "`\n" +
                    "📧 អុីមែល ៖ `" + (userInfo.email() != null ? userInfo.email() : "N/A") + "`\n" +
                    "🏪 ហាង ៖ *" + setting.getBusiness().getDisplayName() + "*\n" +
                    uiHelper.divider() + "សូមស្វាគមន៍ការត្រលប់មកវិញ! ឥឡូវនេះអ្នកអាចបញ្ជាទិញទំនិញបានហើយ។";

            telegramBotClient.sendMessage(botToken, chatId, successMsg);
            sendMainMenu(botToken, chatId, session, setting);
        } catch (Exception e) {
            log.error("Database linking failed during login: {}", e.getMessage(), e);
            telegramBotClient.sendMessage(botToken, chatId,
                    "⚠️ មានបញ្ហាក្នុងការភ្ជាប់ទិន្នន័យគណនីក្នុង Database។ សូមព្យាយាមម្ដងទៀត។",
                    TelegramKeyboards.backToMenu());
            session.setState(STATE_IDLE);
        }
    }

    private void startRegistration(String botToken, Long chatId, BotSession session) {
        if (session.getCustomer() != null) {
            telegramBotClient.sendMessage(botToken, chatId, "✅ អ្នកបានចូលគណនីរួចរាល់ហើយ។",
                    TelegramKeyboards.backToMenu());
            return;
        }
        session.setState(STATE_REGISTER_USERNAME);
        session.setContext(new HashMap<>());

        List<List<InlineKeyboardButton>> cancelBtn = List
                .of(List.of(new InlineKeyboardButton("❌ បោះបង់ (Cancel)", "auth:cancel")));
        String prompt = uiHelper.header("📝", "ចុះឈ្មោះគណនីថ្មី (STEP 1/6)") +
                "សូមវាយ **ឈ្មោះគណនី (Username)** ដែលអ្នកចង់បង្កើត៖\n_(ឧទាហរណ៍៖ kakaka)_";
        telegramBotClient.sendMessage(botToken, chatId, prompt, cancelBtn);
    }

    private void handleRegisterUsername(String botToken, Long chatId, String text, BotSession session) {
        if (!StringUtils.hasText(text) || text.length() < 3 || text.contains(" ")) {
            telegramBotClient.sendMessage(botToken, chatId,
                    "⚠️ Username ត្រូវមានយ៉ាងតិច ៣ អក្សរ និងមិនត្រូវមានដកឃ្លាទេ៖");
            return;
        }
        session.getContext().put("reg_username", text.trim());
        session.setState(STATE_REGISTER_PASSWORD);

        List<List<InlineKeyboardButton>> cancelBtn = List
                .of(List.of(new InlineKeyboardButton("❌ បោះបង់ (Cancel)", "auth:cancel")));
        String prompt = uiHelper.header("🔑", "កំណត់ពាក្យសម្ងាត់ (STEP 2/6)") +
                "សូមកំណត់ **ពាក្យសម្ងាត់ (Password)** សម្រាប់គណនី `" + text.trim()
                + "` ៖\n_(ពាក្យសម្ងាត់ត្រូវមានយ៉ាងតិច ៨ ខ្ទង់ ឧ. P@ssw0rd123)_";
        telegramBotClient.sendMessage(botToken, chatId, prompt, cancelBtn);
    }

    private void handleRegisterPassword(String botToken, Long chatId, String text, BotSession session) {
        if (!StringUtils.hasText(text) || text.length() < 8) {
            telegramBotClient.sendMessage(botToken, chatId,
                    "⚠️ ពាក្យសម្ងាត់ត្រូវមានយ៉ាងតិច **៨ ខ្ទង់ឡើងទៅ**។ សូមវាយម្ដងទៀត៖");
            return;
        }
        session.getContext().put("reg_password", text.trim());
        session.setState(STATE_REGISTER_NAME);

        List<List<InlineKeyboardButton>> cancelBtn = List
                .of(List.of(new InlineKeyboardButton("❌ បោះបង់ (Cancel)", "auth:cancel")));
        String prompt = uiHelper.header("📛", "ឈ្មោះរបស់អ្នក (STEP 3/6)") +
                "សូមវាយ **ឈ្មោះ និង នាមត្រកូល** របស់អ្នក (First Name & Last Name) ៖\n_(ឧទាហរណ៍៖ Nha Kola)_";
        telegramBotClient.sendMessage(botToken, chatId, prompt, cancelBtn);
    }

    private void handleRegisterName(String botToken, Long chatId, String text, BotSession session) {
        if (!StringUtils.hasText(text) || text.trim().length() < 2) {
            telegramBotClient.sendMessage(botToken, chatId, "⚠️ សូមបញ្ជាក់ឈ្មោះរបស់អ្នកឲ្យបានត្រឹមត្រូវ៖");
            return;
        }

        String[] parts = text.trim().split("\\s+", 2);
        String firstName = parts[0];
        String lastName = parts.length > 1 ? parts[1] : "User";

        session.getContext().put("reg_firstName", firstName);
        session.getContext().put("reg_lastName", lastName);
        session.setState(STATE_REGISTER_EMAIL);

        List<List<InlineKeyboardButton>> cancelBtn = List
                .of(List.of(new InlineKeyboardButton("❌ បោះបង់ (Cancel)", "auth:cancel")));
        String prompt = uiHelper.header("📧", "អុីមែល (STEP 4/6)") +
                "សូមវាយ **អុីមែល (Email)** របស់អ្នក៖\n_(ឧទាហរណ៍៖ sengkim@gmail.com)_";
        telegramBotClient.sendMessage(botToken, chatId, prompt, cancelBtn);
    }

    private void handleRegisterEmail(String botToken, Long chatId, String text, BotSession session) {
        if (!StringUtils.hasText(text) || !text.contains("@")) {
            telegramBotClient.sendMessage(botToken, chatId,
                    "⚠️ ទម្រង់អុីមែលមិនត្រឹមត្រូវទេ។ សូមវាយអុីមែលពិតប្រាកដរបស់អ្នក៖");
            return;
        }
        session.getContext().put("reg_email", text.trim());
        session.setState(STATE_REGISTER_PHONE);

        List<List<InlineKeyboardButton>> cancelBtn = List
                .of(List.of(new InlineKeyboardButton("❌ បោះបង់ (Cancel)", "auth:cancel")));
        String prompt = uiHelper.header("📱", "លេខទូរស័ព្ទ (STEP 5/6)") +
                "សូមវាយ **លេខទូរស័ព្ទ** របស់អ្នក៖\n_(ឧទាហរណ៍៖ 09975498587)_";
        telegramBotClient.sendMessage(botToken, chatId, prompt, cancelBtn);
    }

    private void handleRegisterPhone(String botToken, Long chatId, String text, BotSession session) {
        if (!StringUtils.hasText(text) || !PHONE_PATTERN.matcher(text).matches() || text.length() < 8) {
            telegramBotClient.sendMessage(botToken, chatId,
                    "⚠️ លេខទូរស័ព្ទមិនត្រឹមត្រូវទេ។ សូមវាយលេខទូរស័ព្ទពិតប្រាកដ (យ៉ាងតិច ៨ ខ្ទង់)៖");
            return;
        }
        session.getContext().put("reg_phone", text.replaceAll("\\s+", "").trim());

        List<List<InlineKeyboardButton>> genderButtons = List.of(
                List.of(new InlineKeyboardButton("👨 MALE (ប្រុស)", "auth:reg:gender:MALE"),
                        new InlineKeyboardButton("👩 FEMALE (ស្រី)", "auth:reg:gender:FEMALE")),
                List.of(new InlineKeyboardButton("⚪ OTHER (ផ្សេងៗ)", "auth:reg:gender:OTHER"),
                        new InlineKeyboardButton("🔒 UNSPECIFIED (មិនបញ្ជាក់)", "auth:reg:gender:UNSPECIFIED")),
                List.of(new InlineKeyboardButton("❌ បោះបង់ (Cancel)", "auth:cancel")));

        String prompt = uiHelper.header("🚻", "ភេទ (STEP 6/6)")
                + "ជំហានចុងក្រោយ! សូមជ្រើសរើស **ភេទ (Gender)** របស់អ្នក៖";
        telegramBotClient.sendMessage(botToken, chatId, prompt, genderButtons);
    }

    private void executeRealRegistration(String botToken, Long chatId, String gender, BusinessTelegramBot setting,
            BotSession session) {
        String username = String.valueOf(session.getContext().get("reg_username"));
        String password = String.valueOf(session.getContext().get("reg_password"));
        String firstName = String.valueOf(session.getContext().get("reg_firstName"));
        String lastName = String.valueOf(session.getContext().get("reg_lastName"));
        String email = String.valueOf(session.getContext().get("reg_email"));
        String phone = String.valueOf(session.getContext().get("reg_phone"));

        try {
            RegisterRequest request = new RegisterRequest(
                    username, password, password, email, firstName, lastName, phone, gender);

            authService.register(request, RoleEnum.CUSTOMER.name());
            log.info("Successfully registered user {} via internal AuthService with Record DTO", username);

            GlobalCustomer globalCustomer = customerIdentityService.resolve(
                    null, email, phone, firstName + " " + lastName);

            Customer customer = findOrCreateCustomer(setting, globalCustomer);
            linkTelegramIdentity(setting, session, customer);

            session.setCustomer(customer);
            session.setState(STATE_IDLE);
            session.getContext().clear();

            String successMsg = uiHelper.header("🎉", "ចុះឈ្មោះជោគជ័យ (REGISTERED)") +
                    "អបអរសាទរ! គណនីរបស់អ្នកត្រូវបានបង្កើតក្នុង Keycloak និងប្រព័ន្ធ DOIFY ដោយជោគជ័យ!\n\n" +
                    "👤 គណនី ៖ *" + username + "* (" + firstName + " " + lastName + ")\n" +
                    "📧 អុីមែល ៖ `" + email + "`\n" +
                    "📱 ទូរស័ព្ទ ៖ `" + phone + "`\n" +
                    "🚻 ភេទ ៖ *" + gender + "*\n" +
                    uiHelper.divider() + "ឥឡូវនេះអ្នកអាចចាប់ផ្តើមទិញទំនិញបានហើយ!";

            telegramBotClient.sendMessage(botToken, chatId, successMsg);
            sendMainMenu(botToken, chatId, session, setting);

        } catch (Exception e) {
            log.warn("Registration failed via AuthService Record DTO: {}", e.getMessage());
            telegramBotClient.sendMessage(botToken, chatId,
                    "❌ ការចុះឈ្មោះបរាជ័យ! ឈ្មោះគណនី, អុីមែល, ឬលេខទូរស័ព្ទនេះ អាចនឹងមានក្នុងប្រព័ន្ធរួចហើយ។ សូមសាកល្បងម្ដងទៀត។",
                    TelegramKeyboards.backToMenu());
            session.setState(STATE_IDLE);
        }
    }

    private Customer findOrCreateCustomer(BusinessTelegramBot setting, GlobalCustomer globalCustomer) {

        return customerIdentityService.customerFor(setting.getBusiness(), globalCustomer);
    }

    private void linkTelegramIdentity(BusinessTelegramBot setting, BotSession session, Customer customer) {
        customerChannelIdentityRepository
                .findByBusiness_IdAndChannelAndExternalId(
                        setting.getBusiness().getId(), ChannelType.TELEGRAM, session.getExternalId())
                .orElseGet(() -> {
                    CustomerChannelIdentity created = new CustomerChannelIdentity();
                    created.setBusiness(setting.getBusiness());
                    created.setCustomer(customer);
                    created.setChannel(ChannelType.TELEGRAM);
                    created.setExternalId(session.getExternalId());
                    return customerChannelIdentityRepository.save(created);
                });
    }

    private void showProfile(String botToken, Long chatId, BotSession session) {
        Customer customer = session.getCustomer();
        if (customer == null || customer.getGlobalCustomer() == null) {
            telegramBotClient.sendMessage(botToken, chatId, "⚠️ អ្នកមិនទាន់បានចូលគណនីនៅឡើយទេ។",
                    TelegramKeyboards.backToMenu());
            return;
        }

        GlobalCustomer globalCustomer = customer.getGlobalCustomer();
        String phoneDisplay = StringUtils.hasText(globalCustomer.getPhoneNumber())
                ? globalCustomer.getPhoneNumber()
                : "មិនទាន់ចែករំលែក (Not Shared)";

        Object tgUsernameObj = session.getContext() != null ? session.getContext().get("tg_username") : null;
        String usernameDisplay = tgUsernameObj != null && StringUtils.hasText(tgUsernameObj.toString())
                ? "@" + tgUsernameObj.toString()
                : (StringUtils.hasText(globalCustomer.getEmail()) ? globalCustomer.getEmail() : "N/A");

        String message = uiHelper.header("👤", "ព័ត៌មានគណនីរបស់អ្នក (USER PROFILE)")
                + "📛 ឈ្មោះ ៖ *" + globalCustomer.getFullName() + "*\n"
                + "👤 Username ៖ `" + usernameDisplay + "`\n"
                + "📱 លេខទូរស័ព្ទ ៖ `" + phoneDisplay + "`\n"
                + "🔒 IAM Status ៖ 🟢 `Keycloak Verified`\n";

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        if (!StringUtils.hasText(globalCustomer.getPhoneNumber())) {
            keyboard.add(List.of(new InlineKeyboardButton("📱 ចែករំលែកលេខទូរស័ព្ទ (Share Phone)", "auth:sharephone")));
        }
        keyboard.add(List.of(new InlineKeyboardButton("🧾 ប្រវត្តិការទិញ", "menu:history")));
        keyboard.add(List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម (Main Menu)", "menu:main")));

        telegramBotClient.sendMessage(botToken, chatId, message, keyboard);
    }

    private void handleLogout(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session) {
        if (session.getCustomer() == null) {
            telegramBotClient.sendMessage(botToken, chatId, "⚠️ អ្នកមិនទាន់បានចូលគណនីនៅឡើយទេ។",
                    TelegramKeyboards.backToMenu());
            return;
        }
        String customerName = session.getCustomer().getGlobalCustomer() != null 
                ? session.getCustomer().getGlobalCustomer().getFullName() 
                : "អតិថិជន";

        try {
            customerChannelIdentityRepository
                    .findByBusiness_IdAndChannelAndExternalId(
                            setting.getBusiness().getId(), ChannelType.TELEGRAM, session.getExternalId())
                    .ifPresent(customerChannelIdentityRepository::delete);
        } catch (Exception e) {
            log.warn("Error deleting channel identity during logout: {}", e.getMessage());
        }

        session.setCustomer(null);
        session.setState(STATE_IDLE);
        botSessionRepository.save(session);

        String logoutMsg = uiHelper.header("🚪", "ចាកចេញពីគណនីជោគជ័យ") +
                "លាហើយ *" + customerName + "*! ការភ្ជាប់អត្តសញ្ញាណត្រូវបានដកចេញពីប្រព័ន្ធដោយជោគជ័យ។";
        telegramBotClient.sendMessage(botToken, chatId, logoutMsg);
        sendMainMenu(botToken, chatId, session, setting);
    }

    private void showCategories(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session) {
        session.getContext().remove("catalogCategory");
        session.getContext().remove("catalogPage");

        // Query sub-categories (parent IS NOT NULL) to avoid empty parent category views
        List<ItemGroup> categories = itemGroupRepository
                .findByBusinessIdAndParentIsNotNullOrderByNameAsc(setting.getBusiness().getId());

        if (categories.isEmpty()) {
            categories = itemGroupRepository
                    .findByBusinessIdAndParentIsNullOrderByNameAsc(setting.getBusiness().getId());
        }

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(List.of(new InlineKeyboardButton("🛍️ ផលិតផលទាំងអស់", "cat:" + CATALOG_TOKEN_ALL)));

        List<InlineKeyboardButton> row = new ArrayList<>();
        for (ItemGroup category : categories) {
            row.add(new InlineKeyboardButton("▫️ " + category.getName(), "cat:" + category.getId()));
            if (row.size() == 2) {
                keyboard.add(List.copyOf(row));
                row.clear();
            }
        }
        if (!row.isEmpty())
            keyboard.add(List.copyOf(row));
        keyboard.add(List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main")));
        telegramBotClient.sendMessage(botToken, chatId, uiHelper.header("🗂️", "ជ្រើសរើសប្រភេទផលិតផល"), keyboard);
    }

    private void handleCatalogPaging(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session,
            boolean forward) {
        Object storedToken = session.getContext().get("catalogCategory");
        if (storedToken == null) {
            showCategories(botToken, chatId, setting, session);
            return;
        }
        int storedPage = session.getContext().get("catalogPage") instanceof Integer p ? p : 0;
        showItemsPage(botToken, chatId, setting, session, String.valueOf(storedToken),
                forward ? storedPage + 1 : Math.max(0, storedPage - 1));
    }

    private void showStoredItemsPage(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session) {
        Object storedToken = session.getContext().get("catalogCategory");
        if (storedToken == null) {
            showCategories(botToken, chatId, setting, session);
            return;
        }
        int storedPage = session.getContext().get("catalogPage") instanceof Integer p ? p : 0;
        showItemsPage(botToken, chatId, setting, session, String.valueOf(storedToken), storedPage);
    }

    private void showItemsPage(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session,
            String catToken, int page) {
        UUID businessId = setting.getBusiness().getId();
        PageRequest pageable = PageRequest.of(page, CATALOG_PAGE_SIZE);
        Page<Item> itemsPage;
        String categoryName;

        if (CATALOG_TOKEN_ALL.equals(catToken)) {
            Specification<Item> spec = Specification.where(ItemSpecifications.hasBusinessId(businessId))
                    .and(ItemSpecifications.hasStatus(ItemStatus.ACTIVE))
                    .and(ItemSpecifications.isEnabledInChannelCodes(List.of("TELEGRAM", "TELEGRAM_BOT")));
            itemsPage = itemRepository.findAll(spec, pageable);
            categoryName = "ផលិតផលទាំងអស់";
        } else {
            UUID groupId;
            try {
                groupId = UUID.fromString(catToken);
            } catch (Exception e) {
                showCategories(botToken, chatId, setting, session);
                return;
            }
            Specification<Item> spec = Specification.where(ItemSpecifications.hasBusinessId(businessId))
                    .and(ItemSpecifications.hasStatus(ItemStatus.ACTIVE))
                    .and(ItemSpecifications.hasItemGroupId(groupId))
                    .and(ItemSpecifications.isEnabledInChannelCodes(List.of("TELEGRAM", "TELEGRAM_BOT")));
            itemsPage = itemRepository.findAll(spec, pageable);
            categoryName = itemGroupRepository.findByIdAndBusinessId(groupId, businessId).map(ItemGroup::getName)
                    .orElse("ប្រភេទផលិតផល");
        }

        session.getContext().put("catalogCategory", catToken);
        session.getContext().put("catalogPage", page);

        if (itemsPage.isEmpty()) {
            telegramBotClient.sendMessage(botToken, chatId,
                    "😔 មិនទាន់មានផលិតផលក្នុងប្រភេទ \"" + categoryName + "\" ទេ។",
                    List.of(List.of(new InlineKeyboardButton("⬅️ ត្រលប់ក្រោយ", "catback"))));
            return;
        }

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        for (Item item : itemsPage.getContent()) {
            String formattedPrice = uiHelper.formatPrice(item.getPrice(), setting).replace("`", "");
            Optional<BigDecimal> availQty = stockHelper.trackedAvailableQuantity(setting.getBusiness().getId(), item, OrderChannel.TELEGRAM);
            boolean oos = availQty.map(qty -> qty.compareTo(BigDecimal.ZERO) <= 0).orElse(false);
            String stockTag = oos ? " [🔴 អស់ស្តុក]" : "";
            keyboard.add(List.of(new InlineKeyboardButton("▫️ " + item.getName() + stockTag + " — [" + formattedPrice + "]",
                    "item:" + item.getId())));
        }

        List<InlineKeyboardButton> pagingRow = new ArrayList<>();
        if (itemsPage.hasPrevious())
            pagingRow.add(new InlineKeyboardButton("⬅️ ទំព័រមុន", "catpage:prev"));
        if (itemsPage.hasNext())
            pagingRow.add(new InlineKeyboardButton("ទំព័របន្ទាប់ ➡️", "catpage:next"));
        if (!pagingRow.isEmpty())
            keyboard.add(List.copyOf(pagingRow));
        keyboard.add(List.of(new InlineKeyboardButton("⬅️ ប្រភេទផលិតផល", "catback")));
        keyboard.add(List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main")));

        telegramBotClient.sendMessage(botToken, chatId,
                uiHelper.header("🗂️", categoryName) + "📑 ទំព័រទី " + (page + 1) + "/" + itemsPage.getTotalPages(),
                keyboard);
    }

    private void showItemDetail(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session,
            String itemIdRaw) {
        UUID itemId;
        try {
            itemId = UUID.fromString(itemIdRaw);
        } catch (Exception e) {
            showCategories(botToken, chatId, setting, session);
            return;
        }
        Item item = itemRepository.findByIdAndBusinessId(itemId, setting.getBusiness().getId()).orElse(null);
        if (item == null) {
            telegramBotClient.sendMessage(botToken, chatId, "😔 ផលិតផលនេះមិនមានលក់ទៀតទេ។",
                    TelegramKeyboards.backToMenu());
            return;
        }

        Optional<BigDecimal> availableQuantity = stockHelper.trackedAvailableQuantity(setting.getBusiness().getId(),
                item, OrderChannel.TELEGRAM);
        boolean outOfStock = availableQuantity.map(qty -> qty.compareTo(BigDecimal.ZERO) <= 0).orElse(false);

        List<kh.edu.istad.ite.features.discount.dto.DiscountResponse> applicable = discountService.findApplicableDiscounts(
                setting.getBusiness().getId(),
                kh.edu.istad.ite.shared.enums.OrderChannel.TELEGRAM,
                item.getId(),
                item.getItemGroup() != null ? item.getItemGroup().getId() : null
        );
        kh.edu.istad.ite.features.discount.dto.DiscountResponse discount = applicable.isEmpty() ? null : applicable.get(0);

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        if (!outOfStock) {
            if (item.getVariants() != null && !item.getVariants().isEmpty()) {
                keyboard.addAll(variantButtons(item, setting, discount));
            } else {
                if (item.getColors() != null && !item.getColors().isEmpty()) {
                    keyboard.addAll(colorButtons(item, setting));
                }
                if (item.getAttributes() != null && !item.getAttributes().isEmpty()) {
                    keyboard.addAll(attributeButtons(item, setting));
                }
                keyboard.add(List.of(new InlineKeyboardButton("🛒 ថែមចូលកន្ត្រក", "cart:add:" + itemId)));
            }
        }
        keyboard.add(List.of(new InlineKeyboardButton("⬅️ ត្រលប់ក្រោយ", "itemback")));

        String detailText = uiHelper.renderProductDetail(item, setting, availableQuantity, discount);
        Optional<String> imageUrl = item.getImages().stream()
                .findFirst()
                .map(image -> minioService.getPublicUrl(image.getImageKey()));
        if (imageUrl.isPresent())
            telegramBotClient.sendPhoto(botToken, chatId, imageUrl.get(), detailText, keyboard);
        else
            telegramBotClient.sendMessage(botToken, chatId, detailText, keyboard);
    }

    private void startSearch(String botToken, Long chatId, BotSession session) {
        session.setState(STATE_SEARCH_AWAITING_KEYWORD);
        telegramBotClient.sendMessage(botToken, chatId,
                uiHelper.header("🔎", "ស្វែងរកផលិតផល") + "សូមវាយឈ្មោះផលិតផលដែលអ្នកចង់ស្វែងរក ៖",
                List.of(List.of(new InlineKeyboardButton("❌ បោះបង់ការស្វែងរក", "search:cancel"))));
    }

    private void handleSearchKeyword(String botToken, Long chatId, String keyword, BusinessTelegramBot setting,
            BotSession session) {
        Specification<Item> spec = Specification.where(ItemSpecifications.hasBusinessId(setting.getBusiness().getId()))
                .and(ItemSpecifications.hasStatus(ItemStatus.ACTIVE))
                .and(ItemSpecifications.nameContainsIgnoreCase(keyword))
                .and(ItemSpecifications.isEnabledInChannelCodes(List.of("TELEGRAM", "TELEGRAM_BOT")));
        Page<Item> searchResults = itemRepository.findAll(spec, PageRequest.of(0, 10));
        session.setState(STATE_IDLE);
        if (searchResults.isEmpty()) {
            telegramBotClient.sendMessage(botToken, chatId, "❌ រកមិនឃើញផលិតផលឈ្មោះ *" + keyword + "* ទេ។",
                    List.of(List.of(new InlineKeyboardButton("🔎 ស្វែងរកម្ដងទៀត", "menu:search")),
                            List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main"))));
            return;
        }
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        for (Item item : searchResults.getContent()) {
            String formattedPrice = uiHelper.formatPrice(item.getPrice(), setting).replace("`", "");
            Optional<BigDecimal> availQty = stockHelper.trackedAvailableQuantity(setting.getBusiness().getId(), item, OrderChannel.TELEGRAM);
            boolean oos = availQty.map(qty -> qty.compareTo(BigDecimal.ZERO) <= 0).orElse(false);
            String stockTag = oos ? " [🔴 អស់ស្តុក]" : "";
            keyboard.add(List.of(new InlineKeyboardButton("▫️ " + item.getName() + stockTag + " — [" + formattedPrice + "]",
                    "item:" + item.getId())));
        }
        keyboard.add(List.of(new InlineKeyboardButton("🔎 ស្វែងរកម្ដងទៀត", "menu:search")));
        keyboard.add(List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main")));
        telegramBotClient.sendMessage(botToken, chatId, uiHelper.header("🔍", "លទ្ធផលស្វែងរក៖ " + keyword) + "រកឃើញ *"
                + searchResults.getTotalElements() + "* ផលិតផល៖", keyboard);
    }

    private List<List<InlineKeyboardButton>> variantButtons(Item item, BusinessTelegramBot setting, kh.edu.istad.ite.features.discount.dto.DiscountResponse discount) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (var variant : item.getVariants()) {
            BigDecimal origPrice = variant.getPrice() != null ? variant.getPrice() : item.getPrice();
            BigDecimal finalPrice = origPrice;
            String discountTag = "";
            if (discount != null && origPrice != null) {
                BigDecimal discountAmt = BigDecimal.ZERO;
                if (discount.type() == kh.edu.istad.ite.shared.enums.DiscountType.PERCENTAGE && discount.value() != null) {
                    discountAmt = origPrice.multiply(discount.value()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                } else if (discount.type() == kh.edu.istad.ite.shared.enums.DiscountType.FIXED_AMOUNT && discount.value() != null) {
                    discountAmt = discount.value();
                }
                if (discount.maxDiscountAmount() != null && discountAmt.compareTo(discount.maxDiscountAmount()) > 0) {
                    discountAmt = discount.maxDiscountAmount();
                }
                finalPrice = origPrice.subtract(discountAmt);
                if (finalPrice.compareTo(BigDecimal.ZERO) < 0) finalPrice = BigDecimal.ZERO;
                discountTag = " 🔥";
            }

            String priceStr = uiHelper.formatPrice(finalPrice, setting).replace("`", "");
            String label = "▫️ " + variant.getVariantName() + discountTag + " — [" + priceStr + "]";

            rows.add(List.of(new InlineKeyboardButton(label, "cart:addv:" + variant.getId())));
        }

        return rows;
    }

    private List<List<InlineKeyboardButton>> colorButtons(Item item, BusinessTelegramBot setting) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (item.getColors() == null) return rows;
        for (ItemColor color : item.getColors()) {
            if (!StringUtils.hasText(color.getValue())) continue;
            String label = "🎨 ពណ៌: " + color.getValue();
            rows.add(List.of(new InlineKeyboardButton(label, "cart:addcolor:" + item.getId() + ":" + color.getValue())));
        }
        return rows;
    }

    private List<List<InlineKeyboardButton>> attributeButtons(Item item, BusinessTelegramBot setting) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (item.getAttributes() == null) return rows;
        for (ItemAttribute attr : item.getAttributes()) {
            if (attr.getValues() == null || attr.getValues().isEmpty()) continue;
            for (var val : attr.getValues()) {
                if (!StringUtils.hasText(val.getValue())) continue;
                String label = "⚙️ " + attr.getName() + ": " + val.getValue();
                rows.add(List.of(new InlineKeyboardButton(label, "cart:addattr:" + item.getId() + ":" + attr.getName() + ":" + val.getValue())));
            }
        }
        return rows;
    }

    private void handleAddColorToCart(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session, String data) {
        if (requireLogin(botToken, chatId, session)) return;
        String[] parts = data.split(":", 2);
        if (parts.length < 2) return;
        UUID itemId;
        try {
            itemId = UUID.fromString(parts[0]);
        } catch (Exception e) {
            return;
        }
        String colorValue = parts[1];
        addItemWithSelectionToCart(botToken, chatId, setting, session, itemId, "Color", colorValue, colorValue);
    }

    private void handleAddAttrToCart(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session, String data) {
        if (requireLogin(botToken, chatId, session)) return;
        String[] parts = data.split(":", 2);
        if (parts.length < 2) return;
        UUID itemId;
        try {
            itemId = UUID.fromString(parts[0]);
        } catch (Exception e) {
            return;
        }
        String attrName = parts[1];
        String attrVal = parts.length > 2 ? parts[2] : attrName;
        addItemWithSelectionToCart(botToken, chatId, setting, session, itemId, attrName, attrVal, attrVal);
    }

    private void addItemWithSelectionToCart(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session,
            UUID itemId, String attrName, String value, String labelText) {
        Item item = itemRepository.findByIdAndBusinessId(itemId, setting.getBusiness().getId()).orElse(null);
        if (item == null) return;

        Cart cart = cartRepository
                .findActiveCartWithItems(session.getCustomer().getId(), setting.getBusiness().getId(), CartStatus.ACTIVE)
                .orElseGet(() -> cartRepository.save(Cart.builder().customer(session.getCustomer())
                        .business(setting.getBusiness()).status(CartStatus.ACTIVE).items(new ArrayList<>()).build()));

        String displayName = item.getName() + " (" + labelText + ")";

        if (!stockHelper.hasEnoughStock(setting.getBusiness().getId(), item, null, 1, OrderChannel.TELEGRAM)) {
            telegramBotClient.sendMessage(botToken, chatId,
                    "❌ ស្តុកមិនគ្រប់គ្រាន់សម្រាប់ *" + displayName + "* ទេ។",
                    List.of(List.of(new InlineKeyboardButton("🛍️ ទិញទំនិញបន្ត", "menu:catalog"))));
            return;
        }

        BigDecimal priceSnapshot = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
        CartItem newItem = CartItem.builder().cart(cart).item(item).variant(null).quantity(1)
                .priceSnapshot(priceSnapshot).selections(new ArrayList<>()).build();

        CartItemSelection selection = CartItemSelection.builder()
                .cartItem(newItem)
                .attributeName(attrName)
                .value(value)
                .label(labelText)
                .build();
        newItem.addSelection(selection);

        cartItemRepository.save(newItem);
        cart.getItems().add(newItem);

        telegramBotClient.sendMessage(botToken, chatId, "✅ បានបន្ថែម *" + displayName + "* ចូលកន្ត្រកទំនិញ!",
                List.of(List.of(new InlineKeyboardButton("🛒 មើលកន្ត្រកទំនិញ (" + cart.getTotalItemsCount() + ")", "menu:cart")),
                        List.of(new InlineKeyboardButton("🛍️ ទិញទំនិញបន្ត", "menu:catalog"))));
    }

    private void showVariantPicker(String botToken, Long chatId, BusinessTelegramBot setting, String itemIdRaw) {
        UUID itemId;
        try {
            itemId = UUID.fromString(itemIdRaw);
        } catch (Exception e) {
            return;
        }
        Item item = itemRepository.findByIdAndBusinessId(itemId, setting.getBusiness().getId()).orElse(null);
        if (item == null || item.getVariants().isEmpty())
            return;

        List<kh.edu.istad.ite.features.discount.dto.DiscountResponse> applicable = discountService.findApplicableDiscounts(
                setting.getBusiness().getId(),
                kh.edu.istad.ite.shared.enums.OrderChannel.TELEGRAM,
                item.getId(),
                item.getItemGroup() != null ? item.getItemGroup().getId() : null
        );
        kh.edu.istad.ite.features.discount.dto.DiscountResponse discount = applicable.isEmpty() ? null : applicable.get(0);

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>(variantButtons(item, setting, discount));
        keyboard.add(List.of(new InlineKeyboardButton("⬅️ ត្រលប់ក្រោយ", "item:" + itemId)));

        telegramBotClient.sendMessage(botToken, chatId,
                uiHelper.header("🎛️", item.getName()) + "👇 សូមជ្រើសរើសជម្រើសខាងក្រោម៖", keyboard);
    }

    private void handleAddVariantToCart(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session,
            String data) {
        if (requireLogin(botToken, chatId, session)) {
            return;
        }

        UUID variantId;
        try {
            variantId = UUID.fromString(data);
        } catch (IllegalArgumentException exception) {
            log.warn("Telegram variant callback carried an unparseable id: {}", data);
            telegramBotClient.sendMessage(botToken, chatId,
                    "\u26A0\uFE0F ជម្រើសនេះមិនត្រឹមត្រូវទេ។ សូមជ្រើសរើសម្ដងទៀត។");
            return;
        }

        ItemVariant variant = itemVariantRepository
                .findByIdAndBusiness_Id(variantId, setting.getBusiness().getId())
                .orElse(null);

        if (variant == null) {
            log.warn("Variant {} not found for business {}", variantId, setting.getBusiness().getId());
            telegramBotClient.sendMessage(botToken, chatId,
                    "\u26A0\uFE0F ជម្រើសនេះមិនមានទៀតទេ។ សូមជ្រើសរើសម្ដងទៀត។");
            return;
        }

        handleAddToCart(botToken, chatId, setting, session,
                variant.getItem().getId() + ":" + variant.getId());
    }

    private void handleAddToCart(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session,
            String data) {
        if (requireLogin(botToken, chatId, session)) {
            return;
        }

        String[] parts = data.split(":", 2);
        UUID itemId;
        UUID variantId = null;
        try {
            itemId = UUID.fromString(parts[0]);
            if (parts.length > 1) {
                variantId = UUID.fromString(parts[1]);
            }
        } catch (Exception e) {

            log.warn("Telegram cart callback carried an unparseable id: {}", data);
            return;
        }

        Item item = itemRepository.findByIdAndBusinessId(itemId, setting.getBusiness().getId()).orElse(null);
        if (item == null)
            return;

        if (!item.getVariants().isEmpty() && variantId == null) {
            showVariantPicker(botToken, chatId, setting, itemId.toString());
            return;
        }

        ItemVariant variant = null;
        if (variantId != null) {
            final UUID vid = variantId;
            variant = item.getVariants().stream()
                    .filter(candidate -> candidate.getId().equals(vid))
                    .findFirst()
                    .orElse(null);
            if (variant == null)
                return;
        }

        Cart cart = cartRepository
                .findActiveCartWithItems(session.getCustomer().getId(), setting.getBusiness().getId(),
                        CartStatus.ACTIVE)
                .orElseGet(() -> cartRepository.save(Cart.builder().customer(session.getCustomer())
                        .business(setting.getBusiness()).status(CartStatus.ACTIVE).items(new ArrayList<>()).build()));

        Optional<CartItem> existingItemOpt = variant == null
                ? cartItemRepository.findByCartIdAndItemIdAndVariantIsNull(cart.getId(), item.getId())
                : cartItemRepository.findByCartIdAndItemIdAndVariant_Id(cart.getId(), item.getId(), variant.getId());
        int quantityAlreadyInCart = existingItemOpt.map(CartItem::getQuantity).orElse(0);

        String displayName = variant != null ? item.getName() + " (" + variant.getVariantName() + ")" : item.getName();

        if (!stockHelper.hasEnoughStock(
                setting.getBusiness().getId(), item, variant, quantityAlreadyInCart + 1,
                OrderChannel.TELEGRAM)) {
            telegramBotClient.sendMessage(botToken, chatId,
                    "❌ ស្តុកមិនគ្រប់គ្រាន់សម្រាប់ *" + displayName + "* ទេ។ សូមកាត់បន្ថយចំនួន ឬជ្រើសរើសទំនិញផ្សេង។",
                    List.of(List.of(new InlineKeyboardButton("🛍️ ទិញទំនិញបន្ត", "menu:catalog")),
                            List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main"))));
            return;
        }

        if (existingItemOpt.isPresent()) {
            CartItem ci = existingItemOpt.get();
            ci.setQuantity(ci.getQuantity() + 1);
            cartItemRepository.save(ci);

            cart.getItems().stream()
                    .filter(existing -> existing.getId().equals(ci.getId()))
                    .findFirst()
                    .ifPresent(existing -> existing.setQuantity(ci.getQuantity()));
        } else {
            BigDecimal priceSnapshot = variant != null && variant.getPrice() != null ? variant.getPrice()
                    : item.getPrice();
            CartItem newItem = CartItem.builder().cart(cart).item(item).variant(variant).quantity(1)
                    .priceSnapshot(priceSnapshot).build();
            cartItemRepository.save(newItem);
            cart.getItems().add(newItem);
        }
        telegramBotClient.sendMessage(botToken, chatId, "✅ បានបន្ថែម *" + displayName + "* ចូលកន្ត្រកទំនិញ!",
                List.of(List.of(new InlineKeyboardButton("🛒 មើលកន្ត្រកទំនិញ (" + cart.getTotalItemsCount() + ")",
                        "menu:cart")), List.of(new InlineKeyboardButton("🛍️ ទិញទំនិញបន្ត", "menu:catalog"))));
    }

    private void showCart(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session) {
        if (requireLogin(botToken, chatId, session)) {
            return;
        }
        Optional<Cart> cartOpt = cartRepository.findActiveCartWithItems(session.getCustomer().getId(),
                setting.getBusiness().getId(), CartStatus.ACTIVE);
        if (cartOpt.isEmpty() || cartOpt.get().getItems().isEmpty()) {
            telegramBotClient.sendMessage(botToken, chatId,
                    uiHelper.header("🛒", "កន្ត្រកទំនិញ") + "😔 កន្ត្រកទំនិញរបស់អ្នកកំពុងទទេស្អាត!",
                    List.of(List.of(new InlineKeyboardButton("🛍️ មើលបញ្ជីទំនិញ", "menu:catalog")),
                            List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main"))));
            return;
        }
        Cart cart = cartOpt.get();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        for (CartItem ci : cart.getItems()) {
            String shortName = ci.getItem().getName().length() > 15 ? ci.getItem().getName().substring(0, 12) + "..."
                    : ci.getItem().getName();
            keyboard.add(List.of(new InlineKeyboardButton("🔴 ➖", "cart:minus:" + ci.getId()),
                    new InlineKeyboardButton("▫️ " + shortName + " (" + ci.getQuantity() + ")",
                            "item:" + ci.getItem().getId()),
                    new InlineKeyboardButton("🟢 ➕", "cart:plus:" + ci.getId()),
                    new InlineKeyboardButton("🗑️", "cart:rm:" + ci.getId())));
        }
        keyboard.add(List.of(new InlineKeyboardButton("🛍️ ទិញទំនិញបន្ត", "menu:catalog")));
        keyboard.add(List.of(new InlineKeyboardButton("💳 គិតលុយ (Checkout Now)", "menu:checkout")));
        keyboard.add(List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main")));
        
        String customerName = session.getCustomer() != null && session.getCustomer().getGlobalCustomer() != null
                ? session.getCustomer().getGlobalCustomer().getFullName()
                : "អតិថិជន";
                
        telegramBotClient.sendMessage(botToken, chatId,
                uiHelper.renderCartReceipt(cart, setting, customerName, discountService),
                keyboard);
    }

    private void updateCartItemQty(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session,
            String cartItemIdRaw, int delta) {
        try {
            Optional<CartItem> itemOpt = cartItemRepository.findById(UUID.fromString(cartItemIdRaw));
            if (itemOpt.isPresent()) {
                CartItem item = itemOpt.get();
                int newQty = item.getQuantity() + delta;
                if (newQty <= 0) {
                    cartItemRepository.delete(item);
                } else if (delta > 0
                        && !stockHelper.hasEnoughStock(
                                setting.getBusiness().getId(), item.getItem(), item.getVariant(), newQty,
                                OrderChannel.TELEGRAM)) {
                    telegramBotClient.sendMessage(botToken, chatId,
                            "❌ ស្តុកមិនគ្រប់គ្រាន់សម្រាប់ *" + item.getItem().getName() + "* ទេ។");
                } else {
                    item.setQuantity(newQty);
                    cartItemRepository.save(item);
                }
            }
        } catch (Exception e) {
            log.error("Error updating qty: {}", e.getMessage());
        }
        showCart(botToken, chatId, setting, session);
    }

    private void removeCartItem(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session,
            String cartItemIdRaw) {
        try {
            cartItemRepository.deleteById(UUID.fromString(cartItemIdRaw));
        } catch (Exception e) {
            log.error("Error removing item: {}", e.getMessage());
        }
        showCart(botToken, chatId, setting, session);
    }
}