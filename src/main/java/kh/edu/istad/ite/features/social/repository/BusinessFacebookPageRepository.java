package kh.edu.istad.ite.features.social.repository;

import kh.edu.istad.ite.features.social.entity.BusinessFacebookPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessFacebookPageRepository extends JpaRepository<BusinessFacebookPage, UUID> {

    Optional<BusinessFacebookPage> findByBusinessId(UUID businessId);

    Optional<BusinessFacebookPage> findByPageId(String pageId);
}
