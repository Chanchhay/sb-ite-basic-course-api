package kh.edu.istad.ite.features.social.service;

import kh.edu.istad.ite.config.security.CredentialCipher;
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
import kh.edu.istad.ite.features.social.entity.BotSession;
import kh.edu.istad.ite.features.social.entity.BusinessTelegramBot;
import kh.edu.istad.ite.features.social.repository.BotSessionRepository;
import kh.edu.istad.ite.features.social.repository.BusinessTelegramBotRepository;
import kh.edu.istad.ite.features.social.telegram.InlineKeyboardButton;
import kh.edu.istad.ite.features.social.telegram.TelegramBotClient;
import kh.edu.istad.ite.features.social.telegram.TelegramCallbackQuery;
import kh.edu.istad.ite.features.social.telegram.TelegramKeyboards;
import kh.edu.istad.ite.features.social.telegram.TelegramUpdate;
import kh.edu.istad.ite.shared.enums.ChannelType;
import kh.edu.istad.ite.shared.enums.BusinessFeature;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramWebhookServiceImpl implements TelegramWebhookService {

    private static final String STATE_IDLE = "IDLE";
    private static final String STATE_REGISTER_AWAITING_NAME = "REGISTER_AWAITING_NAME";
    private static final String STATE_REGISTER_AWAITING_PHONE = "REGISTER_AWAITING_PHONE";

    private static final Set<String> REQUIRES_REGISTRATION = Set.of(
            "menu:cart", "menu:checkout", "menu:orders", "menu:profile", "menu:location", "menu:history");

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9 ]{8,20}$");

    private static final int CATALOG_PAGE_SIZE = 5;
    private static final String CATALOG_TOKEN_ALL = "all";

    private final BusinessTelegramBotRepository telegramBotRepository;
    private final BotSessionRepository botSessionRepository;
    private final CustomerChannelIdentityRepository customerChannelIdentityRepository;
    private final CustomerRepository customerRepository;
    private final GlobalCustomerRepository globalCustomerRepository;
    private final ItemRepository itemRepository;
    private final ItemGroupRepository itemGroupRepository;
    private final CredentialCipher credentialCipher;
    private final TelegramBotClient telegramBotClient;
    private final BusinessHelper businessHelper;

    @Override
    @Transactional
    public void handleUpdate(String webhookSecret, String secretTokenHeader, TelegramUpdate update) {
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
            handleCallback(botToken, chatId, callbackQuery.data(), setting, session);
        } else {
            String text = update.message().text() == null ? "" : update.message().text().trim();
            handleText(botToken, chatId, text, setting, session);
        }

        session.setUpdatedAt(LocalDateTime.now());
        botSessionRepository.save(session);
    }

    private Long resolveChatId(TelegramUpdate update) {
        if (update == null) {
            return null;
        }
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
                    return created;
                });
    }

    private void attachExistingCustomerIfAny(BusinessTelegramBot setting, BotSession session) {
        if (session.getCustomer() != null) {
            return;
        }

        customerChannelIdentityRepository
                .findByBusiness_IdAndChannelAndExternalId(
                        setting.getBusiness().getId(), ChannelType.TELEGRAM, session.getExternalId())
                .ifPresent(identity -> session.setCustomer(identity.getCustomer()));
    }


    private void handleText(String botToken, Long chatId, String text, BusinessTelegramBot setting, BotSession session) {
        String state = session.getState() == null ? STATE_IDLE : session.getState();

        switch (state) {
            case STATE_REGISTER_AWAITING_NAME -> handleRegisterName(botToken, chatId, text, session);
            case STATE_REGISTER_AWAITING_PHONE -> handleRegisterPhone(botToken, chatId, text, setting, session);
            default -> handleIdleText(botToken, chatId, text, setting, session);
        }
    }

    private void handleIdleText(String botToken, Long chatId, String text, BusinessTelegramBot setting, BotSession session) {
        if ("/start".equalsIgnoreCase(text)) {
            String greeting = StringUtils.hasText(setting.getWelcomeMessage())
                    ? setting.getWelcomeMessage()
                    : "Welcome to " + setting.getBusiness().getDisplayName() + "! 👋";
            telegramBotClient.sendMessage(botToken, chatId, greeting);
            sendMainMenu(botToken, chatId, session);
            return;
        }

        if ("/help".equalsIgnoreCase(text)) {
            telegramBotClient.sendMessage(botToken, chatId,
                    "Available commands:\n/start - greeting + main menu\n/help - show this help");
            return;
        }

        sendMainMenu(botToken, chatId, session);
    }

    private void sendMainMenu(String botToken, Long chatId, BotSession session) {
        boolean registered = session.getCustomer() != null;
        telegramBotClient.sendMessage(botToken, chatId, "What would you like to do?",
                TelegramKeyboards.mainMenu(registered));
    }


    private void handleCallback(String botToken, Long chatId, String data, BusinessTelegramBot setting, BotSession session) {
        if (data == null) {
            return;
        }

        if (REQUIRES_REGISTRATION.contains(data) && session.getCustomer() == null) {
            telegramBotClient.sendMessage(botToken, chatId,
                    "🔐 Please register first to use this feature.",
                    List.of(List.of(new InlineKeyboardButton("📝 Register / Login", "auth:register:start"))));
            return;
        }

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

        if (data.startsWith("cart:add:")) {
            telegramBotClient.sendMessage(botToken, chatId,
                    "🚧 Add to Cart is coming in a future update.", TelegramKeyboards.backToMenu());
            return;
        }

        switch (data) {
            case "menu:main" -> sendMainMenu(botToken, chatId, session);

            case "auth:register:start" -> startRegistration(botToken, chatId, session);

            case "menu:profile" -> showProfile(botToken, chatId, session);

            case "menu:search", "menu:cart", "menu:checkout",
                 "menu:orders", "menu:history", "menu:location" -> sendComingSoon(botToken, chatId, data);

            default -> telegramBotClient.sendMessage(botToken, chatId,
                    "Sorry, I didn't understand that.", TelegramKeyboards.backToMenu());
        }
    }

    private void sendComingSoon(String botToken, Long chatId, String menuKey) {
        telegramBotClient.sendMessage(botToken, chatId,
                "🚧 This feature is coming in a future update.", TelegramKeyboards.backToMenu());
    }

    private void showProfile(String botToken, Long chatId, BotSession session) {
        Customer customer = session.getCustomer();
        GlobalCustomer globalCustomer = customer.getGlobalCustomer();
        String message = "👤 *Your Profile*\nName: " + globalCustomer.getFullName()
                + "\nPhone: " + globalCustomer.getPhoneNumber();
        telegramBotClient.sendMessage(botToken, chatId, message, TelegramKeyboards.backToMenu());
    }


    private void showCategories(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session) {
        session.getContext().remove("catalogCategory");
        session.getContext().remove("catalogPage");

        List<ItemGroup> categories = itemGroupRepository
                .findByBusinessIdAndParentIsNullOrderByNameAsc(setting.getBusiness().getId());

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(List.of(new InlineKeyboardButton("🛍️ All Products", "cat:" + CATALOG_TOKEN_ALL)));

        List<InlineKeyboardButton> row = new ArrayList<>();
        for (ItemGroup category : categories) {
            row.add(new InlineKeyboardButton(category.getName(), "cat:" + category.getId()));
            if (row.size() == 2) {
                keyboard.add(List.copyOf(row));
                row.clear();
            }
        }
        if (!row.isEmpty()) {
            keyboard.add(List.copyOf(row));
        }
        keyboard.add(List.of(new InlineKeyboardButton("⬅️ Main Menu", "menu:main")));

        telegramBotClient.sendMessage(botToken, chatId, "🗂️ Browse by category:", keyboard);
    }

    private void handleCatalogPaging(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session, boolean forward) {
        Object storedToken = session.getContext().get("catalogCategory");
        if (storedToken == null) {
            showCategories(botToken, chatId, setting, session);
            return;
        }
        int storedPage = session.getContext().get("catalogPage") instanceof Integer p ? p : 0;
        int nextPage = forward ? storedPage + 1 : Math.max(0, storedPage - 1);
        showItemsPage(botToken, chatId, setting, session, String.valueOf(storedToken), nextPage);
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
            itemsPage = itemRepository.findByBusinessIdAndStatusOrderByNameAsc(businessId, ItemStatus.ACTIVE, pageable);
            categoryName = "All Products";
        } else {
            UUID groupId;
            try {
                groupId = UUID.fromString(catToken);
            } catch (IllegalArgumentException exception) {
                showCategories(botToken, chatId, setting, session);
                return;
            }
            itemsPage = itemRepository.findByBusinessIdAndStatusAndItemGroup_IdOrderByNameAsc(
                    businessId, ItemStatus.ACTIVE, groupId, pageable);
            categoryName = itemGroupRepository.findByIdAndBusinessId(groupId, businessId)
                    .map(ItemGroup::getName).orElse("Category");
        }

        session.getContext().put("catalogCategory", catToken);
        session.getContext().put("catalogPage", page);

        if (itemsPage.isEmpty()) {
            telegramBotClient.sendMessage(botToken, chatId,
                    "😔 No products found in \"" + categoryName + "\" yet.",
                    List.of(List.of(new InlineKeyboardButton("⬅️ Categories", "catback"))));
            return;
        }

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        for (Item item : itemsPage.getContent()) {
            String label = item.getName() + " — " + formatPrice(item.getPrice(), setting);
            keyboard.add(List.of(new InlineKeyboardButton(label, "item:" + item.getId())));
        }

        List<InlineKeyboardButton> pagingRow = new ArrayList<>();
        if (itemsPage.hasPrevious()) {
            pagingRow.add(new InlineKeyboardButton("⬅️ Prev", "catpage:prev"));
        }
        if (itemsPage.hasNext()) {
            pagingRow.add(new InlineKeyboardButton("Next ➡️", "catpage:next"));
        }
        if (!pagingRow.isEmpty()) {
            keyboard.add(List.copyOf(pagingRow));
        }
        keyboard.add(List.of(new InlineKeyboardButton("⬅️ Categories", "catback")));

        String header = "🗂️ *" + categoryName + "* (page " + (page + 1) + "/" + itemsPage.getTotalPages() + ")";
        telegramBotClient.sendMessage(botToken, chatId, header, keyboard);
    }

    private void showItemDetail(String botToken, Long chatId, BusinessTelegramBot setting, BotSession session, String itemIdRaw) {
        UUID itemId;
        try {
            itemId = UUID.fromString(itemIdRaw);
        } catch (IllegalArgumentException exception) {
            showCategories(botToken, chatId, setting, session);
            return;
        }

        Item item = itemRepository.findByIdAndBusinessId(itemId, setting.getBusiness().getId()).orElse(null);

        List<List<InlineKeyboardButton>> keyboard = List.of(
                List.of(new InlineKeyboardButton("🛒 Add to Cart", "cart:add:" + itemId)),
                List.of(new InlineKeyboardButton("⬅️ Back to list", "itemback"))
        );

        if (item == null) {
            telegramBotClient.sendMessage(botToken, chatId, "😔 This product is no longer available.",
                    TelegramKeyboards.backToMenu());
            return;
        }

        StringBuilder detail = new StringBuilder();
        detail.append("🛍️ *").append(item.getName()).append("*\n");
        detail.append("💵 ").append(formatPrice(item.getPrice(), setting)).append("\n");
        if (item.getItemGroup() != null) {
            detail.append("🗂️ ").append(item.getItemGroup().getName()).append("\n");
        }
        if (StringUtils.hasText(item.getDescription())) {
            detail.append("\n").append(item.getDescription());
        }

        if (StringUtils.hasText(item.getImageUrl())) {
            telegramBotClient.sendPhoto(botToken, chatId, item.getImageUrl(), detail.toString(), keyboard);
        } else {
            telegramBotClient.sendMessage(botToken, chatId, detail.toString(), keyboard);
        }
    }

    private String formatPrice(BigDecimal price, BusinessTelegramBot setting) {
        if (price == null) {
            return "—";
        }
        String currency = setting.getBusiness().getDisplayCurrency() != null
                ? setting.getBusiness().getDisplayCurrency()
                : setting.getBusiness().getBaseCurrency();
        return price.setScale(2, java.math.RoundingMode.HALF_UP) + " " + currency;
    }

    // ---------- registration flow ----------

    private void startRegistration(String botToken, Long chatId, BotSession session) {
        if (session.getCustomer() != null) {
            telegramBotClient.sendMessage(botToken, chatId, "✅ You're already registered and logged in.",
                    TelegramKeyboards.backToMenu());
            return;
        }

        session.setState(STATE_REGISTER_AWAITING_NAME);
        session.setContext(new HashMap<>());
        telegramBotClient.sendMessage(botToken, chatId, "📝 Let's get you registered.\nWhat's your full name?");
    }

    private void handleRegisterName(String botToken, Long chatId, String text, BotSession session) {
        if (!StringUtils.hasText(text) || text.length() < 2 || text.length() > 200) {
            telegramBotClient.sendMessage(botToken, chatId, "Please enter a valid name (2-200 characters).");
            return;
        }

        session.getContext().put("pendingName", text);
        session.setState(STATE_REGISTER_AWAITING_PHONE);
        telegramBotClient.sendMessage(botToken, chatId,
                "Thanks, " + text + "! Now please share your phone number (e.g. 012345678).");
    }

    private void handleRegisterPhone(String botToken, Long chatId, String text, BusinessTelegramBot setting, BotSession session) {
        if (!StringUtils.hasText(text) || !PHONE_PATTERN.matcher(text).matches()) {
            telegramBotClient.sendMessage(botToken, chatId,
                    "That doesn't look like a valid phone number. Please try again (digits only, 8-20 characters).");
            return;
        }

        String name = String.valueOf(session.getContext().get("pendingName"));
        String phone = text.trim();

        GlobalCustomer globalCustomer = globalCustomerRepository.findByPhoneNumber(phone)
                .orElseGet(() -> {
                    GlobalCustomer created = new GlobalCustomer();
                    created.setFullName(name);
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
        session.getContext().remove("pendingName");

        telegramBotClient.sendMessage(botToken, chatId, "🎉 You're all set, " + name + "! You're now registered.");
        sendMainMenu(botToken, chatId, session);
    }
}
