package kh.edu.istad.ite.features.discount.service;

import kh.edu.istad.ite.features.discount.dto.CouponResponse;
import kh.edu.istad.ite.features.discount.dto.CreateCouponRequest;
import kh.edu.istad.ite.features.discount.dto.PatchCouponRequest;
import kh.edu.istad.ite.features.discount.dto.UpdateCouponRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public interface CouponService {

    CouponResponse create(CreateCouponRequest request);

    List<CouponResponse> findAll();

    CouponResponse findById(UUID id);

    CouponResponse update(UUID id, UpdateCouponRequest request);

    CouponResponse patch(UUID id, PatchCouponRequest request);

    void delete(UUID id);

}
