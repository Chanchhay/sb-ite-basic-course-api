package kh.edu.istad.ite.features.discount.mapper;

import kh.edu.istad.ite.features.discount.dto.CouponResponse;
import kh.edu.istad.ite.features.discount.dto.CreateCouponRequest;
import kh.edu.istad.ite.features.discount.entity.Coupon;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING
)

public interface CouponMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usedCount", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "discount", ignore = true)
    @Mapping(target = "business", ignore = true)
    Coupon toEntity(CreateCouponRequest request);

    @Mapping(target = "discountId", source = "discount.id")
    @Mapping(target = "discountName", source = "discount.name")

    @Mapping(target = "businessId", source = "business.id")
    @Mapping(target = "businessName", source = "business.displayName")
    CouponResponse toResponse(Coupon coupon);

}
