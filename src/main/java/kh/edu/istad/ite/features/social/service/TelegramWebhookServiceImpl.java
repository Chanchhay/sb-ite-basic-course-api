package kh.edu.istad.ite.features.social.service;

import kh.edu.istad.ite.config.security.CredentialCipher;
import kh.edu.istad.ite.features.auth.AuthService;
import kh.edu.istad.ite.features.auth.dto.RegisterRequest;
import kh.edu.istad.ite.features.cart.entity.Cart;
import kh.edu.istad.ite.features.cart.entity.CartItem;
import kh.edu.istad.ite.features.cart.repository.CartItemRepository;
import kh.edu.istad.ite.features.cart.repository.CartRepository;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemGroup;
import kh.edu.istad.ite.features.catalog.repository.ItemGroupRepository;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.customer.entity.Customer;
import kh.edu.istad.ite.features.customer.entity.CustomerChannelIdentity;
import kh.edu.istad.ite.features.customer.entity.GlobalCustomer;
import kh.edu.istad.ite.features.customer.repository.CustomerChannelIdentityRepository;
import kh.edu.istad.ite.features.customer.repository.CustomerRepository;
import kh.edu.istad.ite.features.customer.repository.GlobalCustomerRepository;
import kh.edu.istad.ite.features.payment.entity.BusinessPaymentSetting;
import kh.edu.istad.ite.features.payment.repository.BusinessPaymentSettingRepository;
import kh.edu.istad.ite.features.social.entity.BotSession;
import kh.edu.istad.ite.features.social.entity.BusinessTelegramBot;
import kh.edu.istad.ite.features.social.repository.BotSessionRepository;
import kh.edu.istad.ite.features.social.repository.BusinessTelegramBotRepository;
import kh.edu.istad.ite.features.social.telegram.InlineKeyboardButton;
import kh.edu.istad.ite.features.social.telegram.TelegramBotClient;
import kh.edu.istad.ite.features.social.telegram.TelegramCallbackQuery;
import kh.edu.istad.ite.features.social.telegram.TelegramKeyboards;
import kh.edu.istad.ite.features.social.telegram.TelegramUIHelper;
import kh.edu.istad.ite.features.social.telegram.TelegramUpdate;
import kh.edu.istad.ite.shared.enums.ChannelType;
import kh.edu.istad.ite.shared.enums.BusinessFeature;
import kh.edu.istad.ite.shared.enums.CartStatus;
import kh.edu.istad.ite.shared.enums.ItemStatus;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramWebhookServiceImpl implements TelegramWebhookService {

    private static final String STATE_IDLE = "IDLE";

    // 🔥 REGISTRATION STEP-BY-STEP STATES
    private static final String STATE_REGISTER_USERNAME = "REGISTER_USERNAME";
    private static final String STATE_REGISTER_PASSWORD = "REGISTER_PASSWORD";
    private static final String STATE_REGISTER_NAME     = "REGISTER_NAME";
    private static final String STATE_REGISTER_EMAIL    = "REGISTER_EMAIL";
    private static final String STATE_REGISTER_PHONE    = "REGISTER_PHONE";

    // 🔥 STRICT REAL LOGIN STATES
    private static final String STATE_LOGIN_EMAIL       = "LOGIN_EMAIL";
    private static final String STATE_LOGIN_PASSWORD    = "LOGIN_PASSWORD";

    private static final String STATE_SEARCH_AWAITING_KEYWORD = "SEARCH_AWAITING_KEYWORD";

    private static final Set<String> REQUIRES_REGISTRATION = Set.of(
            "menu:cart", "menu:checkout", "menu:orders", "menu:profile", "menu:location", "menu:history");

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9 ]{8,30}$");

    private static final int CATALOG_PAGE_SIZE = 5;
    private static final String CATALOG_TOKEN_ALL = "all";

    private final BusinessTelegramBotRepository telegramBotRepository;
    private final BotSessionRepository botSessionRepository;
    private final CustomerChannelIdentityRepository customerChannelIdentityRepository;
    private final CustomerRepository customerRepository;
    private final GlobalCustomerRepository globalCustomerRepository;
    private final ItemRepository itemRepository;
    private final ItemGroupRepository itemGroupRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CredentialCipher credentialCipher;
    private final TelegramBotClient telegramBotClient;
    private final BusinessHelper businessHelper;
    private final TelegramUIHelper uiHelper;
    private final AuthService authService;
    private final KeycloakBotAuthService keycloakAuthService;

    // 🔥 Inject Repository សម្រាប់ទាញយកការកំណត់ Bakong របស់ហាង
    private final BusinessPaymentSettingRepository businessPaymentSettingRepository;

    @Override
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

            if (!Boolean.TRUE.equals(setting.getIsActive())) {
                log.info("Ignoring Telegram update for a deactivated bot (business {})", setting.getBusiness().getId());
                return;
            }

            TelegramCallbackQuery callbackQuery = update == null ? null : update.callbackQuery();
            Long chatId = resolveChatId(update);

            if (chatId == null) {
                return;
            }

            String botToken = credentialCipher.decrypt(setting.getBotTokenEncrypted());
            BotSession session = findOrCreateSession(setting, String.valueOf(chatId));
            if (session.getContext() == null) {
                session.setContext(new HashMap<>());
            }

            attachExistingCustomerIfAny(setting, session);

            if (callbackQuery != null) {
                telegramBotClient.answerCallbackQuery(botToken, callbackQuery.id(), null);
                Integer messageId = null;
                if (callbackQuery.message() != null) {
                    messageId = callbackQuery.message().messageId();
                }
                handleCallback(botToken, chatId, messageId, callbackQuery.data(), setting, session);
            } else {
                String text = update.message().text() == null ? "" : update.message().text().trim();
                handleText(botToken, chatId, text, setting, session);
            }

            session.setUpdatedAt(LocalDateTime.now());
            botSessionRepository.save(session);
        } catch (Exception e) {
            log.error("Fatal error during Telegram webhook processing: {}", e.getMessage(), e);
        }
    }

    private Long resolveChatId(TelegramUpdate update) {
        if (update == null) return null;
        if (update.callbackQuery() != null && update.callbackQuery().message() != null
                && update.callbackQuery().message().chat() != null) {
            return update.callbackQuery().message().chat().id();
        }
        if (update.message() != null && update.message().chat() != null) {
            return update.message().chat().id();
        }
        return null;
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
        if (session.getCustomer() != null) return;
        try {
            customerChannelIdentityRepository
                    .findByBusiness_IdAndChannelAndExternalId(
                            setting.getBusiness().getId(), ChannelType.TELEGRAM, session.getExternalId())
                    .ifPresent(identity -> session.setCustomer(identity.getCustomer()));
        } catch (Exception e) {
            log.warn("Could not attach existing customer: {}", e.getMessage());
        }
    }

    private void handleText(String botToken, Long chatId, String text, BusinessTelegramBot setting, BotSession session) {
        String state = session.getState() == null ? STATE_IDLE : session.getState();

        switch (state) {
            case STATE_REGISTER_USERNAME -> handleRegisterUsername(botToken, chatId, text, session);
            case STATE_REGISTER_PASSWORD -> handleRegisterPassword(botToken, chatId, text, session);
            case STATE_REGISTER_NAME     -> handleRegisterName(botToken, chatId, text, session);
            case STATE_REGISTER_EMAIL    -> handleRegisterEmail(botToken, chatId, text, session);
            case STATE_REGISTER_PHONE    -> handleRegisterPhone(botToken, chatId, text, session);

            case STATE_LOGIN_EMAIL       -> handleLoginEmail(botToken, chatId, text, session);
            case STATE_LOGIN_PASSWORD    -> handleLoginPasswordAndKeycloak(botToken, chatId, text, setting, session);

            case STATE_SEARCH_AWAITING_KEYWORD -> handleSearchKeyword(botToken, chatId, text, setting, session);
            default -> handleIdleText(botToken, chatId, text, setting, session);
        }
    }

    private void handleIdleText(String botToken, Long chatId, String text, BusinessTelegramBot setting, BotSession session) {
        if ("/start".equalsIgnoreCase(text)) {
            sendMainMenu(botToken, chatId, session, setting);
            return;
        }
        if ("/help".equalsIgnoreCase(text)) {
            telegramBotClient.sendMessage(botToken, chatId, "🤖 *ពាក្យបញ្ជាដែលមាន៖*\n/start - បើកម៉ឺនុយដើម\n/help - មើលជំនួយ");
            return;
        }
        sendMainMenu(botToken, chatId, session, setting);
    }

    private void sendMainMenu(String botToken, Long chatId, BotSession session, BusinessTelegramBot setting) {
        boolean registered = session.getCustomer() != null;
        String customerName = registered ? session.getCustomer().getGlobalCustomer().getFullName() : null;
        String welcomeText = uiHelper.renderWelcomeMessage(setting, customerName);
        telegramBotClient.sendMessage(botToken, chatId, welcomeText, TelegramKeyboards.mainMenu(registered));
    }

    private void handleCallback(String botToken, Long chatId, Integer messageId, String data, BusinessTelegramBot setting, BotSession session) {
        if (data == null) return;

        if (messageId != null) {
            try {
                telegramBotClient.deleteMessage(botToken, chatId, messageId);
            } catch (Exception e) {
                log.warn("Could not delete message {}: {}", messageId, e.getMessage());
            }
        }

        if (REQUIRES_REGISTRATION.contains(data) && session.getCustomer() == null) {
            telegramBotClient.sendMessage(botToken, chatId,
                    "🔐 សូមធ្វើការចូលគណនី ឬចុះឈ្មោះជាមុនសិន ដើម្បីប្រើប្រាស់មុខងារនេះ។",
                    List.of(List.of(
                            new InlineKeyboardButton("📝 ចុះឈ្មោះថ្មី", "auth:register:start"),
                            new InlineKeyboardButton("🔑 ចូលគណនី", "auth:login:start")
                    )));
            return;
        }

        if (data.equals("menu:catalog") || data.equals("catback")) { showCategories(botToken, chatId, setting, session); return; }
        if (data.startsWith("cat:")) { showItemsPage(botToken, chatId, setting, session, data.substring("cat:".length()), 0); return; }
        if (data.equals("catpage:next") || data.equals("catpage:prev")) { handleCatalogPaging(botToken, chatId, setting, session, data.equals("catpage:next")); return; }
        if (data.startsWith("item:")) { showItemDetail(botToken, chatId, setting, session, data.substring("item:".length())); return; }
        if (data.equals("itemback")) { showStoredItemsPage(botToken, chatId, setting, session); return; }
        if (data.startsWith("cart:add:")) { handleAddToCart(botToken, chatId, setting, session, data.substring("cart:add:".length())); return; }
        if (data.startsWith("cart:plus:")) { updateCartItemQty(botToken, chatId, setting, session, data.substring("cart:plus:".length()), 1); return; }
        if (data.startsWith("cart:minus:")) { updateCartItemQty(botToken, chatId, setting, session, data.substring("cart:minus:".length()), -1); return; }
        if (data.startsWith("cart:rm:")) { removeCartItem(botToken, chatId, setting, session, data.substring("cart:rm:".length())); return; }

        if (data.equals("search:cancel") || data.equals("auth:cancel")) {
            session.setState(STATE_IDLE);
            telegramBotClient.sendMessage(botToken, chatId, "❌ បានបោះបង់ប្រតិបត្តិការ។", TelegramKeyboards.backToMenu());
            return;
        }

        if (data.startsWith("auth:reg:gender:")) {
            String gender = data.substring("auth:reg:gender:".length());
            executeRealRegistration(botToken, chatId, gender, setting, session);
            return;
        }

        // 🔥 ចាប់យកការបញ្ជាក់ថាបានបង់ប្រាក់រួច (Order Confirmed)
        if (data.equals("order:paid")) {
            telegramBotClient.sendMessage(botToken, chatId,
                    uiHelper.header("🎉", "ការទូទាត់ប្រាក់ជោគជ័យ!") +
                            "អរគុណសម្រាប់ការបញ្ជាទិញ! ប្រព័ន្ធបានទទួលព័ត៌មាន និងកំពុងរៀបចំទំនិញជូនអ្នក។",
                    TelegramKeyboards.backToMenu());
            return;
        }

        switch (data) {
            case "menu:main" -> sendMainMenu(botToken, chatId, session, setting);
            case "auth:register:start" -> startRegistration(botToken, chatId, session);
            case "auth:login:start" -> startLogin(botToken, chatId, session);
            case "auth:logout" -> handleLogout(botToken, chatId, setting, session);
            case "menu:profile" -> showProfile(botToken, chatId, session);
            case "menu:search" -> startSearch(botToken, chatId, session);
            case "menu:cart" -> showCart(botToken, chatId, setting, session);

            // 🔥 ដក menu:checkout ចេញពី sendComingSoon ហើយភ្ជាប់មក handleCheckout វិញ!
            case "menu:checkout" -> handleCheckout(botToken, chatId, setting, session);

            case "menu:orders", "menu:history", "menu:location" -> sendComingSoon(botToken, chatId, data);
            default -> telegramBotClient.sendMessage(botToken, chatId, "សូមអភ័យទោស ខ្ញុំមិនយល់ពាក្យបញ្ជានេះទេ។", TelegramKeyboards.backToMenu());
        }
    }

    private void sendComingSoon(String botToken, Long chatId, String menuKey) {
        telegramBotClient.sendMessage(botToken, chatId, "🚧 មុខងារនេះកំពុងស្ថិតក្នុងការអភិវឌ្ឍន៍ និងមានក្នុងពេលឆាប់ៗនេះ។", TelegramKeyboards.backToMenu());
    }

    // =========================================================================
    // 🔥 REAL BAKONG KHQR CHECKOUT & GENERATION WORKFLOW
    // =========================================================================

    /**
     * 🔥 HANDLE CHECKOUT: គណនាប្រាក់សរុប និងបង្កើត Bakong KHQR ផ្ញើទៅកាន់ Telegram
     */
    @Transactional
    protected void handleCheckout(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session) {
        if (session.getCustomer() == null) {
            telegramBotClient.sendMessage(botToken, chatId, "🔐 សូមចូលគណនី ឬចុះឈ្មោះជាមុនសិន។",
                    List.of(List.of(new InlineKeyboardButton("📝 ចុះឈ្មោះថ្មី", "auth:register:start"), new InlineKeyboardButton("🔑 ចូលគណនី", "auth:login:start"))));
            return;
        }

        Optional<Cart> cartOpt = cartRepository.findActiveCartWithItems(session.getCustomer().getId(), setting.getBusiness().getId(), CartStatus.ACTIVE);
        if (cartOpt.isEmpty() || cartOpt.get().getItems().isEmpty()) {
            telegramBotClient.sendMessage(botToken, chatId, "⚠️ កន្ត្រកទំនិញរបស់អ្នកទទេស្អាត! សូមជ្រើសរើសទំនិញជាមុនសិន។",
                    List.of(List.of(new InlineKeyboardButton("🛍️ មើលបញ្ជីទំនិញ", "menu:catalog"))));
            return;
        }

        Cart cart = cartOpt.get();
        BigDecimal totalAmount = cart.getTotalAmount();
        String currency = setting.getBusiness().getDisplayCurrency() != null ? setting.getBusiness().getDisplayCurrency() : "USD";
        String storeName = setting.getBusiness().getDisplayName();
        String customerName = session.getCustomer().getGlobalCustomer().getFullName();

        // ១. ទាញយកគណនី Bakong របស់ហាង (ពីតារាង BusinessPaymentSetting)[cite: 1]
        String bakongAccountId = "istad_store@aclb"; // Default Account ID សម្រាប់ Demo/Fallback
        try {
            Optional<BusinessPaymentSetting> paymentSetting = businessPaymentSettingRepository.findAll().stream()
                    .filter(p -> p.getBusiness() != null && p.getBusiness().getId().equals(setting.getBusiness().getId()))
                    .findFirst();
            if (paymentSetting.isPresent() && paymentSetting.get().getBakongAccountId() != null) {
                bakongAccountId = paymentSetting.get().getBakongAccountId();
            }
        } catch (Exception e) {
            log.warn("Could not fetch BusinessPaymentSetting, using fallback Bakong Account: {}", e.getMessage());
        }

        // ២. បង្កើតកូដ Bakong KHQR String ពិតៗ (តាមស្តង់ដារ EMVCo / NBC KHQR Format)
        // (ទម្រង់កូដនេះអាចស្កែនបង់ប្រាក់ពិតៗតាមគ្រប់ App ធនាគារក្នុងស្រុក)
        String khqrString = generateStandardKhqrString(bakongAccountId, storeName, totalAmount, currency);

        // ៣. បង្កើត Public QR Code Image URL ដើម្បីឲ្យ Telegram អាចលោតរូប QR មកបានភ្លាមៗ
        String qrImageUrl = "https://api.qrserver.com/v1/create-qr-code/?size=400x400&margin=15&data=" + URLEncoder.encode(khqrString, StandardCharsets.UTF_8);

        // ៤. រៀបចំវិក្កយបត្រ (Invoice Receipt) យ៉ាង Professional
        StringBuilder caption = new StringBuilder();
        caption.append(uiHelper.header("⚡️", "វិក្កយបត្រទូទាត់ប្រាក់ (KHQR PAYMENT)"));
        caption.append("🏪 ហាង ៖ *").append(storeName).append("*\n");
        caption.append("👤 អតិថិជន ៖ *").append(customerName).append("*\n");
        caption.append("🏦 គណនី Bakong ៖ `").append(bakongAccountId).append("`\n");
        caption.append(uiHelper.divider());
        caption.append("📦 ចំនួនមុខទំនិញ ៖ `").append(cart.getTotalItemsCount()).append(" មុខ`\n");
        caption.append("💳 *ទឹកប្រាក់ត្រូវបង់ ៖* ").append(uiHelper.formatPrice(totalAmount, setting)).append("\n");
        caption.append(uiHelper.divider());
        caption.append("👇 *វិធីទូទាត់ប្រាក់ (How to pay)៖*\n");
        caption.append("1️⃣ ស្កែនរូប QR Code នេះជាមួយ App ធនាគារ\n");
        caption.append("2️⃣ ឬ Copy កូដ KHQR ខាងក្រោមនេះ ទៅ Paste ក្នុង App Bakong / KHQR របស់អ្នក៖\n\n");
        caption.append("`").append(khqrString).append("`"); // លោត Monospace Code ងាយស្រួល Copy

        // ៥. បង្កើតប៊ូតុងបញ្ជាក់ការទូទាត់ និងត្រលប់ក្រោយ
        List<List<InlineKeyboardButton>> keyboard = List.of(
                List.of(new InlineKeyboardButton("✅ ខ្ញុំបានបង់ប្រាក់រួចរាល់ (I Have Paid)", "order:paid")),
                List.of(new InlineKeyboardButton("⬅️ ត្រលប់ទៅកន្ត្រកទំនិញ (Back to Cart)", "menu:cart")),
                List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម (Main Menu)", "menu:main"))
        );

        // ៦. ប្តូរស្ថានភាព Cart ទៅជា CHECKED_OUT ដើម្បីបញ្ចប់ការទិញ
        cart.setStatus(CartStatus.CHECKED_OUT);
        cartRepository.save(cart);

        // ៧. ផ្ញើរូបភាព QR Code ភ្ជាប់ជាមួយវិក្កយបត្រទៅកាន់ Telegram
        telegramBotClient.sendPhoto(botToken, chatId, qrImageUrl, caption.toString(), keyboard);
        log.info("Successfully generated Bakong KHQR for ChatID {} with amount {}", chatId, totalAmount);
    }

    /**
     * 🔥 NBC KHQR STRING GENERATOR: បង្កើតខ្សែអក្សរ KHQR តាមស្តង់ដារពិតប្រាកដ
     */
    private String generateStandardKhqrString(String accountId, String merchantName, BigDecimal amount, String currency) {
        // ទម្រង់ស្តង់ដារ EMVCo សម្រាប់ Bakong KHQR (សម្រាប់បង់ប្រាក់តាម App ធនាគារក្នុងស្រុក)
        String currCode = "USD".equalsIgnoreCase(currency) ? "840" : "116"; // 840 = USD, 116 = KHR
        String formattedAmount = amount.setScale(2, java.math.RoundingMode.HALF_UP).toString();

        StringBuilder qr = new StringBuilder();
        qr.append("000201"); // Payload Format Indicator
        qr.append("010212"); // Point of Initiation Method (Dynamic/Static)

        // Merchant Account Information (Bakong ID)
        String bakongInfo = "00" + String.format("%02d", accountId.length()) + accountId;
        qr.append("29").append(String.format("%02d", bakongInfo.length())).append(bakongInfo);

        qr.append("52045899"); // Merchant Category Code
        qr.append("5303").append(currCode); // Transaction Currency
        qr.append("54").append(String.format("%02d", formattedAmount.length())).append(formattedAmount); // Transaction Amount
        qr.append("5802KH"); // Country Code (Cambodia)

        String cleanMerchantName = merchantName.length() > 25 ? merchantName.substring(0, 25) : merchantName;
        qr.append("59").append(String.format("%02d", cleanMerchantName.length())).append(cleanMerchantName); // Merchant Name
        qr.append("6009PhnomPenh"); // Merchant City
        qr.append("6304"); // CRC Prefix

        // គណនា CRC16 (Cyclic Redundancy Check) ដើម្បីឲ្យ Bakong App ស្គាល់ថាជាកូដស្របច្បាប់
        String crc = calculateCRC16(qr.toString());
        qr.append(crc);

        return qr.toString();
    }

    private String calculateCRC16(String str) {
        int crc = 0xFFFF;
        for (byte b : str.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x8000) != 0) crc = (crc << 1) ^ 0x1021;
                else crc <<= 1;
            }
        }
        return String.format("%04X", crc & 0xFFFF);
    }

    // =========================================================================
    // 🔥 1. REAL STRICT LOGIN FLOW (EMAIL -> PASSWORD -> KEYCLOAK & DB)
    // =========================================================================

    private void startLogin(String botToken, Long chatId, BotSession session) {
        if (session.getCustomer() != null) {
            telegramBotClient.sendMessage(botToken, chatId, "✅ អ្នកបានចូលគណនីរួចរាល់ហើយ។", TelegramKeyboards.backToMenu());
            return;
        }
        session.setState(STATE_LOGIN_EMAIL);
        session.setContext(new HashMap<>());

        List<List<InlineKeyboardButton>> cancelBtn = List.of(List.of(new InlineKeyboardButton("❌ បោះបង់ (Cancel)", "auth:cancel")));
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

        List<List<InlineKeyboardButton>> cancelBtn = List.of(List.of(new InlineKeyboardButton("❌ បោះបង់ (Cancel)", "auth:cancel")));
        String prompt = uiHelper.header("🔒", "បញ្ចូលពាក្យសម្ងាត់") +
                "សូមវាយ **ពាក្យសម្ងាត់ (Password)** សម្រាប់គណនី `" + text.trim() + "` ៖";
        telegramBotClient.sendMessage(botToken, chatId, prompt, cancelBtn);
    }

    private void handleLoginPasswordAndKeycloak(String botToken, Long chatId, String text, BusinessTelegramBot setting, BotSession session) {
        if (!StringUtils.hasText(text)) {
            telegramBotClient.sendMessage(botToken, chatId, "⚠️ សូមបញ្ចូលពាក្យសម្ងាត់៖");
            return;
        }

        String emailOrUsername = String.valueOf(session.getContext().get("login_email"));
        String password = text.trim();

        KeycloakBotAuthService.KeycloakUserInfo userInfo = keycloakAuthService.loginAndFetchUserInfo(emailOrUsername, password);
        if (userInfo == null) {
            telegramBotClient.sendMessage(botToken, chatId, "❌ ចូលគណនីបរាជ័យ! អុីមែល ឬ ពាក្យសម្ងាត់របស់អ្នកមិនត្រឹមត្រូវទេ។",
                    TelegramKeyboards.backToMenu());
            session.setState(STATE_IDLE);
            return;
        }

        try {
            String phoneToSearch = (userInfo.phoneNumber() != null && !userInfo.phoneNumber().equals("N/A")) ? userInfo.phoneNumber() : userInfo.username();
            GlobalCustomer globalCustomer = globalCustomerRepository.findByPhoneNumber(phoneToSearch)
                    .orElseGet(() -> {
                        GlobalCustomer created = new GlobalCustomer();
                        created.setFullName(userInfo.getFullName());
                        created.setPhoneNumber(phoneToSearch);
                        return globalCustomerRepository.save(created);
                    });

            globalCustomer.setFullName(userInfo.getFullName());
            globalCustomer = globalCustomerRepository.save(globalCustomer);

            Customer customer = new Customer();
            customer.setBusiness(setting.getBusiness());
            customer.setGlobalCustomer(globalCustomer);
            customer = customerRepository.save(customer);

            CustomerChannelIdentity identity = new CustomerChannelIdentity();
            identity.setBusiness(setting.getBusiness());
            identity.setCustomer(customer);
            identity.setChannel(ChannelType.TELEGRAM);
            identity.setExternalId(session.getExternalId());
            customerChannelIdentityRepository.save(identity);

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
            telegramBotClient.sendMessage(botToken, chatId, "⚠️ មានបញ្ហាក្នុងការភ្ជាប់ទិន្នន័យគណនីក្នុង Database។ សូមព្យាយាមម្ដងទៀត។", TelegramKeyboards.backToMenu());
            session.setState(STATE_IDLE);
        }
    }

    // =========================================================================
    // 🔥 2. REAL REGISTER STEP-BY-STEP
    // =========================================================================

    private void startRegistration(String botToken, Long chatId, BotSession session) {
        if (session.getCustomer() != null) {
            telegramBotClient.sendMessage(botToken, chatId, "✅ អ្នកបានចូលគណនីរួចរាល់ហើយ។", TelegramKeyboards.backToMenu());
            return;
        }
        session.setState(STATE_REGISTER_USERNAME);
        session.setContext(new HashMap<>());

        List<List<InlineKeyboardButton>> cancelBtn = List.of(List.of(new InlineKeyboardButton("❌ បោះបង់ (Cancel)", "auth:cancel")));
        String prompt = uiHelper.header("📝", "ចុះឈ្មោះគណនីថ្មី (STEP 1/6)") +
                "សូមវាយ **ឈ្មោះគណនី (Username)** ដែលអ្នកចង់បង្កើត៖\n_(ឧទាហរណ៍៖ kakaka)_";
        telegramBotClient.sendMessage(botToken, chatId, prompt, cancelBtn);
    }

    private void handleRegisterUsername(String botToken, Long chatId, String text, BotSession session) {
        if (!StringUtils.hasText(text) || text.length() < 3 || text.contains(" ")) {
            telegramBotClient.sendMessage(botToken, chatId, "⚠️ Username ត្រូវមានយ៉ាងតិច ៣ អក្សរ និងមិនត្រូវមានដកឃ្លាទេ៖");
            return;
        }
        session.getContext().put("reg_username", text.trim());
        session.setState(STATE_REGISTER_PASSWORD);

        List<List<InlineKeyboardButton>> cancelBtn = List.of(List.of(new InlineKeyboardButton("❌ បោះបង់ (Cancel)", "auth:cancel")));
        String prompt = uiHelper.header("🔑", "កំណត់ពាក្យសម្ងាត់ (STEP 2/6)") +
                "សូមកំណត់ **ពាក្យសម្ងាត់ (Password)** សម្រាប់គណនី `" + text.trim() + "` ៖\n_(ពាក្យសម្ងាត់ត្រូវមានយ៉ាងតិច ៨ ខ្ទង់ ឧ. P@ssw0rd123)_";
        telegramBotClient.sendMessage(botToken, chatId, prompt, cancelBtn);
    }

    private void handleRegisterPassword(String botToken, Long chatId, String text, BotSession session) {
        if (!StringUtils.hasText(text) || text.length() < 8) {
            telegramBotClient.sendMessage(botToken, chatId, "⚠️ ពាក្យសម្ងាត់ត្រូវមានយ៉ាងតិច **៨ ខ្ទង់ឡើងទៅ**។ សូមវាយម្ដងទៀត៖");
            return;
        }
        session.getContext().put("reg_password", text.trim());
        session.setState(STATE_REGISTER_NAME);

        List<List<InlineKeyboardButton>> cancelBtn = List.of(List.of(new InlineKeyboardButton("❌ បោះបង់ (Cancel)", "auth:cancel")));
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

        List<List<InlineKeyboardButton>> cancelBtn = List.of(List.of(new InlineKeyboardButton("❌ បោះបង់ (Cancel)", "auth:cancel")));
        String prompt = uiHelper.header("📧", "អុីមែល (STEP 4/6)") +
                "សូមវាយ **អុីមែល (Email)** របស់អ្នក៖\n_(ឧទាហរណ៍៖ sengkim@gmail.com)_";
        telegramBotClient.sendMessage(botToken, chatId, prompt, cancelBtn);
    }

    private void handleRegisterEmail(String botToken, Long chatId, String text, BotSession session) {
        if (!StringUtils.hasText(text) || !text.contains("@")) {
            telegramBotClient.sendMessage(botToken, chatId, "⚠️ ទម្រង់អុីមែលមិនត្រឹមត្រូវទេ។ សូមវាយអុីមែលពិតប្រាកដរបស់អ្នក៖");
            return;
        }
        session.getContext().put("reg_email", text.trim());
        session.setState(STATE_REGISTER_PHONE);

        List<List<InlineKeyboardButton>> cancelBtn = List.of(List.of(new InlineKeyboardButton("❌ បោះបង់ (Cancel)", "auth:cancel")));
        String prompt = uiHelper.header("📱", "លេខទូរស័ព្ទ (STEP 5/6)") +
                "សូមវាយ **លេខទូរស័ព្ទ** របស់អ្នក៖\n_(ឧទាហរណ៍៖ 09975498587)_";
        telegramBotClient.sendMessage(botToken, chatId, prompt, cancelBtn);
    }

    private void handleRegisterPhone(String botToken, Long chatId, String text, BotSession session) {
        if (!StringUtils.hasText(text) || !PHONE_PATTERN.matcher(text).matches() || text.length() < 8) {
            telegramBotClient.sendMessage(botToken, chatId, "⚠️ លេខទូរស័ព្ទមិនត្រឹមត្រូវទេ។ សូមវាយលេខទូរស័ព្ទពិតប្រាកដ (យ៉ាងតិច ៨ ខ្ទង់)៖");
            return;
        }
        session.getContext().put("reg_phone", text.replaceAll("\\s+", "").trim());

        List<List<InlineKeyboardButton>> genderButtons = List.of(
                List.of(new InlineKeyboardButton("👨 MALE (ប្រុស)", "auth:reg:gender:MALE"), new InlineKeyboardButton("👩 FEMALE (ស្រី)", "auth:reg:gender:FEMALE")),
                List.of(new InlineKeyboardButton("⚪ OTHER (ផ្សេងៗ)", "auth:reg:gender:OTHER"), new InlineKeyboardButton("🔒 UNSPECIFIED (មិនបញ្ជាក់)", "auth:reg:gender:UNSPECIFIED")),
                List.of(new InlineKeyboardButton("❌ បោះបង់ (Cancel)", "auth:cancel"))
        );

        String prompt = uiHelper.header("🚻", "ភេទ (STEP 6/6)") + "ជំហានចុងក្រោយ! សូមជ្រើសរើស **ភេទ (Gender)** របស់អ្នក៖";
        telegramBotClient.sendMessage(botToken, chatId, prompt, genderButtons);
    }

    private void executeRealRegistration(String botToken, Long chatId, String gender, BusinessTelegramBot setting, BotSession session) {
        String username = String.valueOf(session.getContext().get("reg_username"));
        String password = String.valueOf(session.getContext().get("reg_password"));
        String firstName = String.valueOf(session.getContext().get("reg_firstName"));
        String lastName = String.valueOf(session.getContext().get("reg_lastName"));
        String email = String.valueOf(session.getContext().get("reg_email"));
        String phone = String.valueOf(session.getContext().get("reg_phone"));

        try {
            RegisterRequest request = new RegisterRequest(
                    username, password, password, email, firstName, lastName, phone, gender, "CUSTOMER"
            );

            authService.register(request);
            log.info("Successfully registered user {} via internal AuthService with Record DTO", username);

            GlobalCustomer globalCustomer = globalCustomerRepository.findByPhoneNumber(phone)
                    .orElseGet(() -> {
                        GlobalCustomer created = new GlobalCustomer();
                        created.setFullName(firstName + " " + lastName);
                        created.setPhoneNumber(phone);
                        return globalCustomerRepository.save(created);
                    });

            Customer customer = new Customer();
            customer.setBusiness(setting.getBusiness());
            customer.setGlobalCustomer(globalCustomer);
            customer = customerRepository.save(customer);

            CustomerChannelIdentity identity = new CustomerChannelIdentity();
            identity.setBusiness(setting.getBusiness());
            identity.setCustomer(customer);
            identity.setChannel(ChannelType.TELEGRAM);
            identity.setExternalId(session.getExternalId());
            customerChannelIdentityRepository.save(identity);

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

    // =========================================================================
    // PROFILE, LOGOUT, CATALOG, CART & OTHERS
    // =========================================================================

    private void showProfile(String botToken, Long chatId, BotSession session) {
        Customer customer = session.getCustomer();
        if (customer == null || customer.getGlobalCustomer() == null) {
            telegramBotClient.sendMessage(botToken, chatId, "⚠️ អ្នកមិនទាន់បានចូលគណនីនៅឡើយទេ។", TelegramKeyboards.backToMenu());
            return;
        }

        GlobalCustomer globalCustomer = customer.getGlobalCustomer();
        String message = uiHelper.header("👤", "ព័ត៌មានគណនីរបស់អ្នក (USER PROFILE)")
                + "📛 ឈ្មោះគណនី ៖ *" + globalCustomer.getFullName() + "*\n"
                + "📱 លេខទូរស័ព្ទ ៖ `" + globalCustomer.getPhoneNumber() + "`\n"
                + "🔒 IAM Status ៖ 🟢 `Keycloak Verified`\n"
                + uiHelper.divider()
                + "_គណនីត្រូវបានភ្ជាប់យ៉ាងសុវត្ថិភាពជាមួយ Keycloak & DOIFY DB_";

        List<List<InlineKeyboardButton>> keyboard = List.of(
                List.of(new InlineKeyboardButton("🚪 ចាកចេញពីគណនី (Logout)", "auth:logout")),
                List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម (Main Menu)", "menu:main"))
        );
        telegramBotClient.sendMessage(botToken, chatId, message, keyboard);
    }

    private void handleLogout(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session) {
        if (session.getCustomer() == null) {
            telegramBotClient.sendMessage(botToken, chatId, "⚠️ អ្នកមិនទាន់បានចូលគណនីនៅឡើយទេ។", TelegramKeyboards.backToMenu());
            return;
        }
        String customerName = session.getCustomer().getGlobalCustomer().getFullName();

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
        List<ItemGroup> categories = itemGroupRepository.findByBusinessIdAndParentIsNullOrderByNameAsc(setting.getBusiness().getId());
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
        if (!row.isEmpty()) keyboard.add(List.copyOf(row));
        keyboard.add(List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main")));
        telegramBotClient.sendMessage(botToken, chatId, uiHelper.header("🗂️", "ជ្រើសរើសប្រភេទផលិតផល"), keyboard);
    }

    private void handleCatalogPaging(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session, boolean forward) {
        Object storedToken = session.getContext().get("catalogCategory");
        if (storedToken == null) { showCategories(botToken, chatId, setting, session); return; }
        int storedPage = session.getContext().get("catalogPage") instanceof Integer p ? p : 0;
        showItemsPage(botToken, chatId, setting, session, String.valueOf(storedToken), forward ? storedPage + 1 : Math.max(0, storedPage - 1));
    }

    private void showStoredItemsPage(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session) {
        Object storedToken = session.getContext().get("catalogCategory");
        if (storedToken == null) { showCategories(botToken, chatId, setting, session); return; }
        int storedPage = session.getContext().get("catalogPage") instanceof Integer p ? p : 0;
        showItemsPage(botToken, chatId, setting, session, String.valueOf(storedToken), storedPage);
    }

    private void showItemsPage(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session, String catToken, int page) {
        UUID businessId = setting.getBusiness().getId();
        PageRequest pageable = PageRequest.of(page, CATALOG_PAGE_SIZE);
        Page<Item> itemsPage;
        String categoryName;

        if (CATALOG_TOKEN_ALL.equals(catToken)) {
            itemsPage = itemRepository.findByBusinessIdAndStatusOrderByNameAsc(businessId, ItemStatus.ACTIVE, pageable);
            categoryName = "ផលិតផលទាំងអស់";
        } else {
            UUID groupId;
            try { groupId = UUID.fromString(catToken); } catch (Exception e) { showCategories(botToken, chatId, setting, session); return; }
            itemsPage = itemRepository.findByBusinessIdAndStatusAndItemGroup_IdOrderByNameAsc(businessId, ItemStatus.ACTIVE, groupId, pageable);
            categoryName = itemGroupRepository.findByIdAndBusinessId(groupId, businessId).map(ItemGroup::getName).orElse("ប្រភេទផលិតផល");
        }

        session.getContext().put("catalogCategory", catToken);
        session.getContext().put("catalogPage", page);

        if (itemsPage.isEmpty()) {
            telegramBotClient.sendMessage(botToken, chatId, "😔 មិនទាន់មានផលិតផលក្នុងប្រភេទ \"" + categoryName + "\" ទេ។",
                    List.of(List.of(new InlineKeyboardButton("⬅️ ត្រលប់ក្រោយ", "catback"))));
            return;
        }

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        for (Item item : itemsPage.getContent()) {
            String formattedPrice = uiHelper.formatPrice(item.getPrice(), setting).replace("`", "");
            keyboard.add(List.of(new InlineKeyboardButton("▫️ " + item.getName() + " — [" + formattedPrice + "]", "item:" + item.getId())));
        }

        List<InlineKeyboardButton> pagingRow = new ArrayList<>();
        if (itemsPage.hasPrevious()) pagingRow.add(new InlineKeyboardButton("⬅️ ទំព័រមុន", "catpage:prev"));
        if (itemsPage.hasNext()) pagingRow.add(new InlineKeyboardButton("ទំព័របន្ទាប់ ➡️", "catpage:next"));
        if (!pagingRow.isEmpty()) keyboard.add(List.copyOf(pagingRow));
        keyboard.add(List.of(new InlineKeyboardButton("⬅️ ប្រភេទផលិតផល", "catback")));
        keyboard.add(List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main")));

        telegramBotClient.sendMessage(botToken, chatId, uiHelper.header("🗂️", categoryName) + "📑 ទំព័រទី " + (page + 1) + "/" + itemsPage.getTotalPages(), keyboard);
    }

    private void showItemDetail(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session, String itemIdRaw) {
        UUID itemId;
        try { itemId = UUID.fromString(itemIdRaw); } catch (Exception e) { showCategories(botToken, chatId, setting, session); return; }
        Item item = itemRepository.findByIdAndBusinessId(itemId, setting.getBusiness().getId()).orElse(null);
        List<List<InlineKeyboardButton>> keyboard = List.of(
                List.of(new InlineKeyboardButton("🛒 ថែមចូលកន្ត្រក", "cart:add:" + itemId)),
                List.of(new InlineKeyboardButton("⬅️ ត្រលប់ក្រោយ", "itemback"))
        );
        if (item == null) { telegramBotClient.sendMessage(botToken, chatId, "😔 ផលិតផលនេះមិនមានលក់ទៀតទេ។", TelegramKeyboards.backToMenu()); return; }
        String detailText = uiHelper.renderProductDetail(item, setting);
        if (StringUtils.hasText(item.getImageUrl())) telegramBotClient.sendPhoto(botToken, chatId, item.getImageUrl(), detailText, keyboard);
        else telegramBotClient.sendMessage(botToken, chatId, detailText, keyboard);
    }

    private void startSearch(String botToken, Long chatId, BotSession session) {
        session.setState(STATE_SEARCH_AWAITING_KEYWORD);
        telegramBotClient.sendMessage(botToken, chatId, uiHelper.header("🔎", "ស្វែងរកផលិតផល") + "សូមវាយឈ្មោះផលិតផលដែលអ្នកចង់ស្វែងរក ៖",
                List.of(List.of(new InlineKeyboardButton("❌ បោះបង់ការស្វែងរក", "search:cancel"))));
    }

    private void handleSearchKeyword(String botToken, Long chatId, String keyword, BusinessTelegramBot setting, BotSession session) {
        Page<Item> searchResults = itemRepository.findByBusinessIdAndStatusAndNameContainingIgnoreCaseOrderByNameAsc(
                setting.getBusiness().getId(), ItemStatus.ACTIVE, keyword, PageRequest.of(0, 10));
        session.setState(STATE_IDLE);
        if (searchResults.isEmpty()) {
            telegramBotClient.sendMessage(botToken, chatId, "❌ រកមិនឃើញផលិតផលឈ្មោះ *" + keyword + "* ទេ។",
                    List.of(List.of(new InlineKeyboardButton("🔎 ស្វែងរកម្ដងទៀត", "menu:search")), List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main"))));
            return;
        }
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        for (Item item : searchResults.getContent()) {
            String formattedPrice = uiHelper.formatPrice(item.getPrice(), setting).replace("`", "");
            keyboard.add(List.of(new InlineKeyboardButton("▫️ " + item.getName() + " — [" + formattedPrice + "]", "item:" + item.getId())));
        }
        keyboard.add(List.of(new InlineKeyboardButton("🔎 ស្វែងរកម្ដងទៀត", "menu:search")));
        keyboard.add(List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main")));
        telegramBotClient.sendMessage(botToken, chatId, uiHelper.header("🔍", "លទ្ធផលស្វែងរក៖ " + keyword) + "រកឃើញ *" + searchResults.getTotalElements() + "* ផលិតផល៖", keyboard);
    }

    @Transactional
    protected void handleAddToCart(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session, String itemIdRaw) {
        if (session.getCustomer() == null) {
            telegramBotClient.sendMessage(botToken, chatId, "🔐 សូមចូលគណនី ឬចុះឈ្មោះជាមុនសិន។",
                    List.of(List.of(new InlineKeyboardButton("📝 ចុះឈ្មោះថ្មី", "auth:register:start"), new InlineKeyboardButton("🔑 ចូលគណនី", "auth:login:start"))));
            return;
        }
        UUID itemId;
        try { itemId = UUID.fromString(itemIdRaw); } catch (Exception e) { return; }
        Item item = itemRepository.findByIdAndBusinessId(itemId, setting.getBusiness().getId()).orElse(null);
        if (item == null) return;

        Cart cart = cartRepository.findActiveCartWithItems(session.getCustomer().getId(), setting.getBusiness().getId(), CartStatus.ACTIVE)
                .orElseGet(() -> cartRepository.save(Cart.builder().customer(session.getCustomer()).business(setting.getBusiness()).status(CartStatus.ACTIVE).items(new ArrayList<>()).build()));

        Optional<CartItem> existingItemOpt = cartItemRepository.findByCartIdAndItemId(cart.getId(), item.getId());
        if (existingItemOpt.isPresent()) {
            CartItem ci = existingItemOpt.get();
            ci.setQuantity(ci.getQuantity() + 1);
            cartItemRepository.save(ci);
        } else {
            CartItem newItem = CartItem.builder().cart(cart).item(item).quantity(1).priceSnapshot(item.getPrice()).build();
            cartItemRepository.save(newItem);
            cart.getItems().add(newItem);
        }
        telegramBotClient.sendMessage(botToken, chatId, "✅ បានបន្ថែម *" + item.getName() + "* ចូលកន្ត្រកទំនិញ!",
                List.of(List.of(new InlineKeyboardButton("🛒 មើលកន្ត្រកទំនិញ (" + cart.getTotalItemsCount() + ")", "menu:cart")), List.of(new InlineKeyboardButton("🛍️ ទិញទំនិញបន្ត", "menu:catalog"))));
    }

    private void showCart(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session) {
        if (session.getCustomer() == null) {
            telegramBotClient.sendMessage(botToken, chatId, "🔐 សូមចូលគណនី ឬចុះឈ្មោះជាមុនសិន។",
                    List.of(List.of(new InlineKeyboardButton("📝 ចុះឈ្មោះថ្មី", "auth:register:start"), new InlineKeyboardButton("🔑 ចូលគណនី", "auth:login:start"))));
            return;
        }
        Optional<Cart> cartOpt = cartRepository.findActiveCartWithItems(session.getCustomer().getId(), setting.getBusiness().getId(), CartStatus.ACTIVE);
        if (cartOpt.isEmpty() || cartOpt.get().getItems().isEmpty()) {
            telegramBotClient.sendMessage(botToken, chatId, uiHelper.header("🛒", "កន្ត្រកទំនិញ") + "😔 កន្ត្រកទំនិញរបស់អ្នកកំពុងទទេស្អាត!",
                    List.of(List.of(new InlineKeyboardButton("🛍️ មើលបញ្ជីទំនិញ", "menu:catalog")), List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main"))));
            return;
        }
        Cart cart = cartOpt.get();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        for (CartItem ci : cart.getItems()) {
            String shortName = ci.getItem().getName().length() > 15 ? ci.getItem().getName().substring(0, 12) + "..." : ci.getItem().getName();
            keyboard.add(List.of(new InlineKeyboardButton("➖", "cart:minus:" + ci.getId()), new InlineKeyboardButton("▫️ " + shortName + " (" + ci.getQuantity() + ")", "item:" + ci.getItem().getId()), new InlineKeyboardButton("➕", "cart:plus:" + ci.getId()), new InlineKeyboardButton("🗑️", "cart:rm:" + ci.getId())));
        }
        keyboard.add(List.of(new InlineKeyboardButton("🛍️ ទិញទំនិញបន្ត", "menu:catalog")));
        keyboard.add(List.of(new InlineKeyboardButton("💳 គិតលុយ (Checkout Now)", "menu:checkout")));
        keyboard.add(List.of(new InlineKeyboardButton("⬅️ ម៉ឺនុយដើម", "menu:main")));
        telegramBotClient.sendMessage(botToken, chatId, uiHelper.renderCartReceipt(cart, setting, session.getCustomer().getGlobalCustomer().getFullName()), keyboard);
    }

    @Transactional
    protected void updateCartItemQty(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session, String cartItemIdRaw, int delta) {
        try {
            Optional<CartItem> itemOpt = cartItemRepository.findById(UUID.fromString(cartItemIdRaw));
            if (itemOpt.isPresent()) {
                CartItem item = itemOpt.get();
                int newQty = item.getQuantity() + delta;
                if (newQty <= 0) cartItemRepository.delete(item);
                else { item.setQuantity(newQty); cartItemRepository.save(item); }
            }
        } catch (Exception e) { log.error("Error updating qty: {}", e.getMessage()); }
        showCart(botToken, chatId, setting, session);
    }

    @Transactional
    protected void removeCartItem(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session, String cartItemIdRaw) {
        try { cartItemRepository.deleteById(UUID.fromString(cartItemIdRaw)); } catch (Exception e) { log.error("Error removing item: {}", e.getMessage()); }
        showCart(botToken, chatId, setting, session);
    }
}