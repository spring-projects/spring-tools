package org.test;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepositoryWithAdditionalRepositoryAnnotation extends CrudRepository<Customer, Long> {

    List<Customer> findByLastName(String lastName);
    
    default List<Customer> findByWhatever(String lastName) {return null;}
    
}
