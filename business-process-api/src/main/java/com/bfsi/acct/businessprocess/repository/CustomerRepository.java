package com.bfsi.acct.businessprocess.repository;

import com.bfsi.acct.businessprocess.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for Customer entity (read-only for validations)
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, String> {
    // Inherited methods: findById, existsById, findAll
}
