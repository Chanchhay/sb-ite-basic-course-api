package kh.edu.istad.ite.features.customerdisplay.service;

import kh.edu.istad.ite.shared.helper.BusinessHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerDisplayServiceImpl implements CustomerDisplayService{
    private static final String TOPIC_PREFIX = "/topic/customer-display/";

    private final BusinessHelper businessHelper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publish(UUID businessId,String terminalId, Object payload) {
        businessHelper.findAccessibleBusiness(businessId);
        messagingTemplate.convertAndSend(TOPIC_PREFIX + businessId + "/"+ terminalId, payload);
    }
}
