package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.catalog.dto.CreateProductRequest;
import kh.edu.istad.ite.features.catalog.dto.ProductResponse;
import kh.edu.istad.ite.features.catalog.dto.UpdateProductRequest;

import java.util.List;
import java.util.UUID;

public interface ProductService {

    ProductResponse createProduct(UUID businessId, CreateProductRequest request);

    List<ProductResponse> findAllProducts(UUID businessId);

    ProductResponse findProductById(UUID businessId, UUID productId);

    ProductResponse updateProduct(UUID businessId, UUID productId, UpdateProductRequest request);

    void deleteProduct(UUID businessId, UUID productId);
}
