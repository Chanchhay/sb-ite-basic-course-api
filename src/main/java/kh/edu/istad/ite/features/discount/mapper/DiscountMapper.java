package kh.edu.istad.ite.features.discount.mapper;

import kh.edu.istad.ite.features.discount.dto.CreateDiscountRequest;
import kh.edu.istad.ite.features.discount.dto.DiscountResponse;
import kh.edu.istad.ite.features.discount.dto.PatchDiscountRequest;
import kh.edu.istad.ite.features.discount.entity.Discount;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface DiscountMapper {

    @Mapping(target = "businessId", source = "business.id")
    DiscountResponse toResponse(Discount discount);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "business", ignore = true)
    Discount toEntity(CreateDiscountRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "business", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    void updateEntityFromRequest(CreateDiscountRequest request, @MappingTarget Discount discount);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "business", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    void patchEntityFromRequest(PatchDiscountRequest request, @MappingTarget Discount discount);
}