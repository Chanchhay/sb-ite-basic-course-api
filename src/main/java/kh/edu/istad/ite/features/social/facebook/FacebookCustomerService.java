package kh.edu.istad.ite.features.social.facebook;

import kh.edu.istad.ite.features.customer.entity.Customer;
import kh.edu.istad.ite.features.customer.repository.CustomerRepository;
import kh.edu.istad.ite.features.social.entity.BotSession;
import kh.edu.istad.ite.features.social.entity.BusinessFacebookPage;
import kh.edu.istad.ite.features.social.repository.BotSessionRepository;
import kh.edu.istad.ite.features.order.entity.Order;
import kh.edu.istad.ite.features.order.repository.OrderRepository;
import kh.edu.istad.ite.shared.enums.ChannelType;
import kh.edu.istad.ite.shared.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FacebookCustomerService {

    private final BotSessionRepository botSessionRepository;
    private final CustomerRepository customerRepository;
    private final FacebookGraphClient graphClient;
    private final OrderRepository orderRepository;

    @Transactional
    public BotSession getOrCreateSession(BusinessFacebookPage page, String psid) {
        Optional<BotSession> sessionOpt = botSessionRepository.findByBusiness_IdAndChannelAndExternalId(
                page.getBusiness().getId(), ChannelType.MESSENGER, psid);

        if (sessionOpt.isPresent()) {
            BotSession session = sessionOpt.get();
            session.setUpdatedAt(LocalDateTime.now());
            return botSessionRepository.save(session);
        }

        Map<String, Object> profile = graphClient.getUserProfile(page.getPageAccessTokenEncrypted(), psid);
        String firstName = profile.getOrDefault("first_name", "Customer").toString();
        String lastName = profile.getOrDefault("last_name", "").toString();
        String fullName = (firstName + " " + lastName).trim();

        Customer customer = new Customer();
        customer.setBusiness(page.getBusiness());
        customer.setActive(true);
        customer.setTotalSpend(BigDecimal.ZERO);
        customer.setAddress("Facebook Messenger");
        customer = customerRepository.save(customer);

        BotSession session = new BotSession();
        session.setBusiness(page.getBusiness());
        session.setChannel(ChannelType.MESSENGER);
        session.setExternalId(psid);
        session.setCustomer(customer);
        session.setState("IDLE");
        session.setUpdatedAt(LocalDateTime.now());

        log.info("Auto-registered new Customer {} for Messenger PSID {} ({})", customer.getId(), psid, fullName);
        return botSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public void handleOrderHistory(BusinessFacebookPage page, BotSession session, String psid) {
        Page<Order> orders = orderRepository.findForCustomerByStatuses(
                page.getBusiness().getId(),
                session.getCustomer().getId(),
                List.of(OrderStatus.PENDING, OrderStatus.PAID, OrderStatus.FAILED, OrderStatus.CANCELLED),
                PageRequest.of(0, 5)
        );

        if (orders.isEmpty()) {
            graphClient.sendTextMessage(page.getPageId(), page.getPageAccessTokenEncrypted(), psid,
                    "អ្នកមិនទាន់មានប្រវត្តិបញ្ជាទិញនៅឡើយទេ។");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📝 ប្រវត្តិបញ្ជាទិញ ៥ លើកចុងក្រោយ៖\n\n");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

        for (Order o : orders.getContent()) {
            sb.append("• ").append(o.getInvoiceNumber()).append("\n");
            sb.append("  🗓 ").append(o.getCreatedDate() != null ? o.getCreatedDate().format(dtf) : "N/A").append("\n");
            sb.append("  💰 $").append(o.getTotal().setScale(2)).append("\n");
            
            String status = switch (o.getStatus()) {
                case PENDING -> "កំពុងរង់ចាំ (Pending)";
                case PAID -> "បានទូទាត់ (Paid)";
                case FAILED -> "បរាជ័យ (Failed)";
                case CANCELLED -> "បានលុបចោល (Cancelled)";
                default -> o.getStatus().name();
            };
            sb.append("  📌 ស្ថានភាព៖ ").append(status).append("\n\n");
        }

        List<Map<String, Object>> buttons = List.of(
                Map.of("type", "postback", "title", "🗂️ មើលផលិតផល", "payload", "CATALOG")
        );
        graphClient.sendButtonTemplate(page.getPageId(), page.getPageAccessTokenEncrypted(), psid, sb.toString(), buttons);
    }
}
