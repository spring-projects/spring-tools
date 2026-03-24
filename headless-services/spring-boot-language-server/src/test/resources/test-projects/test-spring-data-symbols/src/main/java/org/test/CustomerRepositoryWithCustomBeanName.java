package org.test;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository("myCustomerRepo")
public interface CustomerRepositoryWithCustomBeanName extends CrudRepository<Customer, Long> {

    List<Customer> findByLastName(String lastName);

}
