package kh.edu.istad.ite.features.business;

import kh.edu.istad.ite.features.business.dto.BusinessCategoryResponse;
import kh.edu.istad.ite.features.business.mapper.BusinessMapper;
import kh.edu.istad.ite.features.business.repository.BusinessCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public/business-categories")
@RequiredArgsConstructor
public class PublicBusinessCategoryController {

    private final BusinessCategoryRepository businessCategoryRepository;
    private final BusinessMapper businessMapper;

    @GetMapping
    public List<BusinessCategoryResponse> getBusinessCategories() {
        return businessCategoryRepository.findAll()
                .stream()
                .map(businessMapper::toCategoryResponse)
                .toList();
    }
}
