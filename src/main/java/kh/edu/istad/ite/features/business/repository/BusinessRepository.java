package kh.edu.istad.ite.features.business.repository;

import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.shared.enums.BusinessOwnerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessRepository extends JpaRepository<Business, UUID>, JpaSpecificationExecutor<Business> {
    boolean existsByKeycloakUserId(UUID keycloakUserId);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    Optional<Business> findByKeycloakUserId(UUID keycloakUserId);

    Optional<Business> findByIdAndKeycloakUserId(UUID id, UUID keycloakUserId);

    /** Ownership check for `BusinessAccessAuthorizationManager`, on every request. */
    boolean existsByIdAndKeycloakUserId(UUID id, UUID keycloakUserId);

    long countByStatus(BusinessOwnerStatus status);

    long countByIsClosedTrue();

    long countByIsListingTrueAndIsClosedFalse();

    long countByCreatedDateGreaterThanEqual(LocalDateTime since);

    boolean existsByBusinessCategory_Id(UUID businessCategoryId);

    @Query("select b.businessCategory.name as categoryName, count(b) as businessCount "
            + "from Business b group by b.businessCategory.name order by count(b) desc")
    List<CategoryCountProjection> countGroupedByCategory();

    @Query(value = "select to_char(created_date, 'YYYY-MM') as month, count(*) as count "
            + "from businesses where created_date >= :since group by month order by month",
            nativeQuery = true)
    List<MonthlyCountProjection> countGroupedByMonth(@Param("since") LocalDateTime since);

        @Query(
            value = """
                    SELECT b FROM Business b
                    WHERE b.isListing = true
                      AND b.isEnabled = true
                      AND b.isClosed = false
                      AND b.status = kh.edu.istad.ite.shared.enums.BusinessOwnerStatus.ACTIVE
                      AND NOT EXISTS (
                          SELECT f FROM BusinessFeatureFlag f
                          WHERE f.business = b
                            AND f.feature = kh.edu.istad.ite.shared.enums.BusinessFeature.STOREFRONT
                            AND f.enabled = false
                      )
                      AND (:categoryId IS NULL OR b.businessCategory.id = :categoryId)
                    ORDER BY COALESCE((SELECT SUM(s.itemCount) FROM Sale s WHERE s.business = b), 0) DESC, b.createdDate DESC
                    """,
            countQuery = """
                    SELECT COUNT(b) FROM Business b
                    WHERE b.isListing = true
                      AND b.isEnabled = true
                      AND b.isClosed = false
                      AND b.status = kh.edu.istad.ite.shared.enums.BusinessOwnerStatus.ACTIVE
                      AND NOT EXISTS (
                          SELECT f FROM BusinessFeatureFlag f
                          WHERE f.business = b
                            AND f.feature = kh.edu.istad.ite.shared.enums.BusinessFeature.STOREFRONT
                            AND f.enabled = false
                      )
                      AND (:categoryId IS NULL OR b.businessCategory.id = :categoryId)
                    """
    )
    Page<Business> findRecommendedStores(@Param("categoryId") UUID categoryId, Pageable pageable);


    interface CategoryCountProjection {
        String getCategoryName();
        Long getBusinessCount();
    }

    interface MonthlyCountProjection {
        String getMonth();
        Long getCount();
    }
}
