package kh.edu.istad.ite.features.business;

import kh.edu.istad.ite.features.business.dto.BusinessCategoryResponse;
import kh.edu.istad.ite.features.business.entity.BusinessCategory;
import kh.edu.istad.ite.features.business.mapper.BusinessMapper;
import kh.edu.istad.ite.features.business.repository.BusinessCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/public/business-categories")
@RequiredArgsConstructor
public class PublicBusinessCategoryController {

    private final BusinessCategoryRepository businessCategoryRepository;
    private final BusinessMapper businessMapper;

    @GetMapping
    public List<BusinessCategoryResponse> getBusinessCategories() {
        Map<UUID, List<BusinessCategory>> subCategoriesByParentId =
                businessCategoryRepository.findByParentCategoryIsNotNullOrderByNameAsc()
                        .stream()
                        .collect(Collectors.groupingBy(category -> category.getParentCategory().getId()));

        return businessCategoryRepository.findByParentCategoryIsNullOrderByNameAsc()
                .stream()
                .map(category -> businessMapper.toCategoryTreeResponse(
                        category,
                        subCategoriesByParentId.getOrDefault(category.getId(), List.of())
                ))
                .toList();
    }
}
