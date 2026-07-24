package kh.edu.istad.ite.features.catalog;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.catalog.dto.CreateProductRequest;
import kh.edu.istad.ite.features.catalog.dto.ProductResponse;
import kh.edu.istad.ite.features.catalog.dto.UpdateProductRequest;
import kh.edu.istad.ite.features.catalog.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ProductResponse createProduct(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateProductRequest request
    ) {
        return productService.createProduct(businessId, request);
    }

    @GetMapping
    public List<ProductResponse> findAllProducts(@PathVariable UUID businessId) {
        return productService.findAllProducts(businessId);
    }

    @GetMapping("/{productId}")
    public ProductResponse findProductById(
            @PathVariable UUID businessId,
            @PathVariable UUID productId
    ) {
        return productService.findProductById(businessId, productId);
    }

    @PutMapping("/{productId}")
    public ProductResponse updateProduct(
            @PathVariable UUID businessId,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        return productService.updateProduct(businessId, productId, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{productId}")
    public void deleteProduct(
            @PathVariable UUID businessId,
            @PathVariable UUID productId
    ) {
        productService.deleteProduct(businessId, productId);
    }
}
