package kh.edu.istad.ite.features.customerdisplay.service;

import java.util.UUID;

public interface CustomerDisplayService {
    void publish(UUID businessId,String terminalId, Object payload);
}
