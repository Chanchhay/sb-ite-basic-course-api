package kh.edu.istad.ite.features.business.repository;

import kh.edu.istad.ite.features.business.entity.BusinessCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BusinessCategoryRepository extends JpaRepository<BusinessCategory, UUID> {

    List<BusinessCategory> findByParentCategoryIsNullOrderByNameAsc();

    List<BusinessCategory> findByParentCategoryIsNotNullOrderByNameAsc();
}
