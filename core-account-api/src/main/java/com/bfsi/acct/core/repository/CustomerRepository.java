package com.bfsi.acct.core.repository;

import com.bfsi.acct.core.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Customer Repository
 * Read-only repository for customer validation
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, String> {
    // Inherited methods:
    // - findById(String customerId): Optional<Customer>
    // - existsById(String customerId): boolean
}
