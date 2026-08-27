package kh.edu.istad.ite.features.customer.service;

import kh.edu.istad.ite.features.customer.dto.CreateCustomerRequest;
import kh.edu.istad.ite.features.customer.dto.CustomerResponse;
import kh.edu.istad.ite.features.customer.dto.UpdateCustomerRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CustomerService {

    CustomerResponse createCustomer(UUID businessId, CreateCustomerRequest request);

    Page<CustomerResponse> findAllCustomers(UUID businessId, Pageable pageable);

    CustomerResponse findCustomerById(UUID businessId, UUID customerId);

    CustomerResponse updateCustomer(UUID businessId, UUID customerId, UpdateCustomerRequest request);

    CustomerResponse activateCustomer(UUID businessId, UUID customerId);

    CustomerResponse deactivateCustomer(UUID businessId, UUID customerId);

    void deleteCustomer(UUID businessId, UUID customerId);
}
