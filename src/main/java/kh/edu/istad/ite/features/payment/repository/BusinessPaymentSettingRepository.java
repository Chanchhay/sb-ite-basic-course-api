package kh.edu.istad.ite.features.payment.repository;

import kh.edu.istad.ite.features.payment.entity.BusinessPaymentSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BusinessPaymentSettingRepository extends JpaRepository<BusinessPaymentSetting, UUID> {

    Optional<BusinessPaymentSetting> findByBusiness_Id(UUID businessId);
}
