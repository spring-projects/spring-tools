package org.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.test.model.Employee;

@SpringBootApplication
public class Application {

	private static final Logger log = LoggerFactory.getLogger(Application.class);

	public static void main(String[] args) {
		SpringApplication.run(Application.class);
	}

	@Bean
	public CommandLineRunner demo(CustomerRepository repository) {
		return args -> {
			Employee employee = new Employee(1234, "Margot", "Al-Harazi");
			// save a couple of customers
			repository.save(new Customer("Jack", "Bauer", employee));
			repository.save(new Customer("Chloe", "O'Brian", employee));
			repository.save(new Customer("Kim", "Bauer", employee));
			repository.save(new Customer("David", "Palmer", employee));
			repository.save(new Customer("Michelle", "Dessler", employee));

			// fetch all customers
			log.info("Customers found with findAll():");
			log.info("-------------------------------");
			for(Customer customer : repository.findAll()){
				log.info(customer.toString());
			}
			log.info("");

			// fetch an individual customer by ID
			Customer customer = repository.findById(1L).get();
			log.info("Customer found with findOne(1L):");
			log.info("--------------------------------");
			log.info(customer.toString());
			log.info("");

			// fetch customers by last name
			log.info("Customer found with findByLastName('Bauer'):");
			log.info("--------------------------------------------");
			for(Customer bauer : repository.findByLastName("Bauer")){
				log.info(bauer.toString());
			}
			log.info("");
		};
	}

}
