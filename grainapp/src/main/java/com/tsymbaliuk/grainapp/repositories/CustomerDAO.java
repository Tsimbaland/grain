package com.tsymbaliuk.grainapp.repositories;

import com.tsymbaliuk.grainapp.domain.Customer;
import org.springframework.data.repository.CrudRepository;

public interface CustomerDAO extends CrudRepository<Customer, Long> {

}
