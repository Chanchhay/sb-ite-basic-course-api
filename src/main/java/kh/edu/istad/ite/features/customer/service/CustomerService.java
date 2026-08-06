package kh.edu.istad.ite.features.customer.service;

import kh.edu.istad.ite.features.customer.dto.CreateCustomerRequest;
import kh.edu.istad.ite.features.customer.dto.CustomerResponse;
import kh.edu.istad.ite.features.customer.dto.UpdateCustomerRequest;

import java.util.List;
import java.util.UUID;

public interface CustomerService {

    CustomerResponse createCustomer(UUID businessId, CreateCustomerRequest request);

    List<CustomerResponse> findAllCustomers(UUID businessId);

    CustomerResponse findCustomerById(UUID businessId, UUID customerId);

    CustomerResponse updateCustomer(UUID businessId, UUID customerId, UpdateCustomerRequest request);

    CustomerResponse activateCustomer(UUID businessId, UUID customerId);

    CustomerResponse deactivateCustomer(UUID businessId, UUID customerId);

    void deleteCustomer(UUID businessId, UUID customerId);
}
