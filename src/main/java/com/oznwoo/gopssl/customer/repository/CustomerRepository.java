package com.oznwoo.gopssl.customer.repository;

import com.oznwoo.gopssl.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Integer> {
}
