package org.test;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

public interface CustomerRepositoryWithTwoParamsMethod extends CrudRepository<Customer, Long> {

    List<Customer> findByLastNameAndStatus(String lastName, Status status);
    
}
