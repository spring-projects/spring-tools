// tag::sample[]
package org.test;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import org.test.model.Employee;
import org.test.model.Person;

@Entity
public class Customer extends Person {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;
	private boolean thisCustomerIsSpecial;//contains keyword in name

	@ManyToOne
	private Employee responsibleEmployee;

	protected Customer() {
		super();
	}

	public Customer(String firstName, String lastName, Employee responsibleEmployee) {
		super(firstName, lastName);
		this.responsibleEmployee = responsibleEmployee;
	}

	@Override
	public String toString() {
		return "Customer [id=" + id + ", firstName=" + firstName + ", lastName=" + lastName + ", responsibleEmployee="
				+ responsibleEmployee + "]";
	}

// end::sample[]

	public Long getId() {
		return id;
	}

	public boolean isThisCustomerIsSpecial() {
		return thisCustomerIsSpecial;
	}

	public Employee getResponsibleEmployee() {
		return responsibleEmployee;
	}
}
