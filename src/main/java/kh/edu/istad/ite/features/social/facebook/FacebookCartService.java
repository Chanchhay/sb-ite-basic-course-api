package kh.edu.istad.ite.features.social.facebook;

import kh.edu.istad.ite.features.cart.entity.Cart;
import kh.edu.istad.ite.features.cart.entity.CartItem;
import kh.edu.istad.ite.features.cart.repository.CartItemRepository;
import kh.edu.istad.ite.features.cart.repository.CartRepository;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.minio.MinioService;
import kh.edu.istad.ite.features.social.entity.BotSession;
import kh.edu.istad.ite.features.social.entity.BusinessFacebookPage;
import kh.edu.istad.ite.shared.enums.CartStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FacebookCartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ItemRepository itemRepository;
    private final FacebookGraphClient graphClient;
    private final MinioService minioService;

    @Transactional
    public void handleAddToCart(BusinessFacebookPage page, BotSession session, String psid, UUID itemId) {
        Item item = itemRepository.findByIdAndBusinessId(itemId, page.getBusiness().getId()).orElse(null);
        if (item == null) {
            graphClient.sendTextMessage(page.getPageId(), page.getPageAccessTokenEncrypted(), psid,
                    "😔 មិនអាចស្វែងរកផលិតផលនេះទេ។");
            return;
        }

        Cart cart = getOrCreateCart(session);
        Optional<CartItem> existingOpt = cart.getItems().stream()
                .filter(ci -> ci.getItem().getId().equals(itemId))
                .findFirst();

        if (existingOpt.isPresent()) {
            CartItem ci = existingOpt.get();
            ci.setQuantity((ci.getQuantity() == null ? 1 : ci.getQuantity()) + 1);
            cartItemRepository.save(ci);
        } else {
            CartItem ci = new CartItem();
            ci.setCart(cart);
            ci.setItem(item);
            ci.setQuantity(1);
            ci.setPriceSnapshot(item.getPrice());
            cartItemRepository.save(ci);
            cart.getItems().add(ci);
        }

        cartRepository.save(cart);

        String text = "✅ បានបញ្ចូល «" + item.getName() + "» ទៅកន្ត្រករបស់អ្នក។";
        List<Map<String, Object>> buttons = List.of(
                Map.of("type", "postback", "title", "🛍️ មើលកន្ត្រក", "payload", "CART_VIEW"),
                Map.of("type", "postback", "title", "💳 គិតលុយ", "payload", "CART_CHECKOUT")
        );
        graphClient.sendButtonTemplate(page.getPageId(), page.getPageAccessTokenEncrypted(), psid, text, buttons);
    }

    @Transactional(readOnly = true)
    public void handleViewCart(BusinessFacebookPage page, BotSession session, String psid) {
        Optional<Cart> cartOpt = cartRepository.findActiveCartWithItems(session.getCustomer().getId(),
                page.getBusiness().getId(), CartStatus.ACTIVE);

        if (cartOpt.isEmpty() || cartOpt.get().getItems().isEmpty()) {
            graphClient.sendTextMessage(page.getPageId(), page.getPageAccessTokenEncrypted(), psid,
                    "កន្ត្រកទំនិញរបស់អ្នកទទេ។ សូមជ្រើសរើសទំនិញសិន!");
            return;
        }

        Cart cart = cartOpt.get();
        BigDecimal total = BigDecimal.ZERO;

        List<Map<String, Object>> elements = new java.util.ArrayList<>();
        for (CartItem ci : cart.getItems()) {
            BigDecimal price = ci.getPriceSnapshot() != null ? ci.getPriceSnapshot() : ci.getItem().getPrice();
            BigDecimal sub = price.multiply(BigDecimal.valueOf(ci.getQuantity()));
            total = total.add(sub);
            
            String subtitle = "តម្លៃ៖ $" + price.setScale(2) + " x " + ci.getQuantity() + " = $" + sub.setScale(2);
            
            elements.add(Map.of(
                    "title", ci.getItem().getName(),
                    "subtitle", subtitle,
                    "image_url", resolveImageUrl(ci.getItem()),
                    "buttons", List.of(
                            Map.of("type", "postback", "title", "🟢 ➕ បូក ១", "payload", "CART_INC:" + ci.getItem().getId()),
                            Map.of("type", "postback", "title", "🔴 ➖ ដក ១", "payload", "CART_DEC:" + ci.getItem().getId()),
                            Map.of("type", "postback", "title", "❌ លុប", "payload", "CART_RM:" + ci.getItem().getId())
                    )
            ));
            
            if (elements.size() >= 10) break; // Messenger limit is 10 elements per generic template
        }

        graphClient.sendGenericTemplate(page.getPageId(), page.getPageAccessTokenEncrypted(), psid, elements);
        
        String totalText = "💰 សរុបទឹកប្រាក់៖ $" + total.setScale(2);
        List<Map<String, Object>> buttons = List.of(
                Map.of("type", "postback", "title", "💳 គិតលុយ", "payload", "CART_CHECKOUT"),
                Map.of("type", "postback", "title", "🗂️ មើលផលិតផលបន្ត", "payload", "CATALOG")
        );
        graphClient.sendButtonTemplate(page.getPageId(), page.getPageAccessTokenEncrypted(), psid, totalText, buttons);
    }
    
    @Transactional
    public void handleIncrementCartItem(BusinessFacebookPage page, BotSession session, String psid, UUID itemId) {
        Cart cart = getOrCreateCart(session);
        Optional<CartItem> existingOpt = cart.getItems().stream()
                .filter(ci -> ci.getItem().getId().equals(itemId))
                .findFirst();
                
        if (existingOpt.isPresent()) {
            CartItem ci = existingOpt.get();
            ci.setQuantity(ci.getQuantity() + 1);
            cartItemRepository.save(ci);
            handleViewCart(page, session, psid);
        }
    }
    
    @Transactional
    public void handleDecrementCartItem(BusinessFacebookPage page, BotSession session, String psid, UUID itemId) {
        Cart cart = getOrCreateCart(session);
        Optional<CartItem> existingOpt = cart.getItems().stream()
                .filter(ci -> ci.getItem().getId().equals(itemId))
                .findFirst();
                
        if (existingOpt.isPresent()) {
            CartItem ci = existingOpt.get();
            if (ci.getQuantity() > 1) {
                ci.setQuantity(ci.getQuantity() - 1);
                cartItemRepository.save(ci);
            } else {
                cart.getItems().remove(ci);
                cartItemRepository.delete(ci);
            }
            handleViewCart(page, session, psid);
        }
    }
    
    @Transactional
    public void handleRemoveCartItem(BusinessFacebookPage page, BotSession session, String psid, UUID itemId) {
        Cart cart = getOrCreateCart(session);
        Optional<CartItem> existingOpt = cart.getItems().stream()
                .filter(ci -> ci.getItem().getId().equals(itemId))
                .findFirst();
                
        if (existingOpt.isPresent()) {
            CartItem ci = existingOpt.get();
            cart.getItems().remove(ci);
            cartItemRepository.delete(ci);
            handleViewCart(page, session, psid);
        }
    }

    private Cart getOrCreateCart(BotSession session) {
        return cartRepository.findActiveCartWithItems(session.getCustomer().getId(),
                session.getBusiness().getId(), CartStatus.ACTIVE).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setCustomer(session.getCustomer());
            cart.setBusiness(session.getBusiness());
            cart.setStatus(CartStatus.ACTIVE);
            
            return cartRepository.save(cart);
        });
    }

    private String resolveImageUrl(Item item) {
        if (item.getImages() != null && !item.getImages().isEmpty()) {
            return minioService.getPublicUrl(item.getImages().get(0).getImageKey());
        }
        if (item.getImageUrl() != null && !item.getImageUrl().isBlank()) {
            return item.getImageUrl();
        }
        return "https://upload.wikimedia.org/wikipedia/commons/1/14/Product_sample_icon_picture.png";
    }
}
