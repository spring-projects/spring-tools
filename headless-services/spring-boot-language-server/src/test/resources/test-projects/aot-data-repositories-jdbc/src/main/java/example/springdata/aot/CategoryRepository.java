package example.springdata.aot;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

interface CategoryRepository extends CrudRepository<Category, Long> {

	List<Category> findAllByNameContaining(String name);

	List<CategoryProjection> findProjectedByNameContaining(String name);

	@Query("SELECT * FROM category WHERE name = :name")
	List<Category> findWithDeclaredQuery(String name);

	List<Category> findByPriorityLevel(PriorityLevel priorityLevel);
}
