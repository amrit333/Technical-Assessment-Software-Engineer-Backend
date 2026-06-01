package com.inventory.order.service;

import com.inventory.order.exception.DuplicateResourceException;
import com.inventory.order.exception.ResourceNotFoundException;
import com.inventory.order.model.Customer;
import com.inventory.order.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Autowired
    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));
    }

    public Customer createCustomer(Customer customer) {
        if (customerRepository.existsByEmail(customer.getEmail())) {
            throw new DuplicateResourceException("Customer with email '" + customer.getEmail() + "' already exists");
        }
        return customerRepository.save(customer);
    }

    public Customer updateCustomer(Long id, Customer details) {
        Customer customer = getCustomerById(id);

        if (!customer.getEmail().equalsIgnoreCase(details.getEmail())) {
            if (customerRepository.existsByEmail(details.getEmail())) {
                throw new DuplicateResourceException("Customer with email '" + details.getEmail() + "' already exists");
            }
            customer.setEmail(details.getEmail());
        }

        customer.setName(details.getName());
        customer.setPhone(details.getPhone());

        return customerRepository.save(customer);
    }

    public void deleteCustomer(Long id) {
        Customer customer = getCustomerById(id);
        customerRepository.delete(customer);
    }
}
