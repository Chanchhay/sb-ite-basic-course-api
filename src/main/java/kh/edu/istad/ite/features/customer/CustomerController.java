package kh.edu.istad.ite.features.customer;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.customer.dto.CreateCustomerRequest;
import kh.edu.istad.ite.features.customer.dto.CustomerResponse;
import kh.edu.istad.ite.features.customer.dto.UpdateCustomerRequest;
import kh.edu.istad.ite.features.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/v1/businesses/{businessId}/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public CustomerResponse createCustomer(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateCustomerRequest request
    ) {
        return customerService.createCustomer(businessId, request);
    }

    @GetMapping
    public List<CustomerResponse> findAllCustomers(@PathVariable UUID businessId) {
        return customerService.findAllCustomers(businessId);
    }

    @GetMapping("/{customerId}")
    public CustomerResponse findCustomerById(
            @PathVariable UUID businessId,
            @PathVariable UUID customerId
    ) {
        return customerService.findCustomerById(businessId, customerId);
    }

    @PatchMapping("/{customerId}")
    public CustomerResponse updateCustomer(
            @PathVariable UUID businessId,
            @PathVariable UUID customerId,
            @Valid @RequestBody UpdateCustomerRequest request
    ) {
        return customerService.updateCustomer(businessId, customerId, request);
    }

    @PatchMapping("/{customerId}/activate")
    public CustomerResponse activateCustomer(
            @PathVariable UUID businessId,
            @PathVariable UUID customerId
    ) {
        return customerService.activateCustomer(businessId, customerId);
    }

    @PatchMapping("/{customerId}/deactivate")
    public CustomerResponse deactivateCustomer(
            @PathVariable UUID businessId,
            @PathVariable UUID customerId
    ) {
        return customerService.deactivateCustomer(businessId, customerId);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{customerId}")
    public void deleteCustomer(
            @PathVariable UUID businessId,
            @PathVariable UUID customerId
    ) {
        customerService.deleteCustomer(businessId, customerId);
    }
}
