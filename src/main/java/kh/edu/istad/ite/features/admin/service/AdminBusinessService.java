package kh.edu.istad.ite.features.admin.service;

import kh.edu.istad.ite.features.admin.dto.request.BusinessStatusActionRequest;
import kh.edu.istad.ite.features.admin.dto.response.ProvinceBackfillResponse;
import kh.edu.istad.ite.features.business.dto.BusinessResponse;
import kh.edu.istad.ite.shared.enums.BusinessOwnerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminBusinessService {

    Page<BusinessResponse> getBusinesses(
            BusinessOwnerStatus status,
            Boolean isEnabled,
            Boolean isClosed,
            UUID categoryId,
            String keyword,
            Pageable pageable
    );

    BusinessResponse getBusiness(UUID businessId);

    BusinessResponse activate(UUID businessId);

    BusinessResponse suspend(UUID businessId, BusinessStatusActionRequest request);

    BusinessResponse enable(UUID businessId);

    BusinessResponse disable(UUID businessId, BusinessStatusActionRequest request);

    BusinessResponse close(UUID businessId, BusinessStatusActionRequest request);

    BusinessResponse reopen(UUID businessId);

    BusinessResponse delete(UUID businessId);

    /**
     * One-time backfill: matches every business's old free-text
     * cityOrProvince against Cambodia's fixed 25-province list and fills in
     * provinceName where a match is found. Never guesses — a business left
     * unmatched keeps its old data untouched, for the owner to fix via the
     * map picker.
     */
    ProvinceBackfillResponse backfillProvinceNames();
}
