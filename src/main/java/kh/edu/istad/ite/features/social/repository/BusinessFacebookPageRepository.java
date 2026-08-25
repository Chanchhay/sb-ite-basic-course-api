package kh.edu.istad.ite.features.social.repository;

import kh.edu.istad.ite.features.social.entity.BusinessFacebookPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessFacebookPageRepository extends JpaRepository<BusinessFacebookPage, UUID> {

    Optional<BusinessFacebookPage> findByBusinessId(UUID businessId);

    @Query("select p from BusinessFacebookPage p join fetch p.business where p.pageId = :pageId")
    Optional<BusinessFacebookPage> findByPageId(@Param("pageId") String pageId);}
