package org.test;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface CustomerRepositoryWithQueryTextBlock extends CrudRepository<Customer, Long> {

	@Query("""
			SELECT ptype
			FROM PetType ptype
			ORDER BY ptype.name
			""")
	List<Object> findPetTypes();
}
