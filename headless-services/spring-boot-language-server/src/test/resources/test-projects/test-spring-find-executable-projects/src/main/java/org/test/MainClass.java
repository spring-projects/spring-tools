package org.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Qualifier;

@SpringBootApplication
public class MainClass {
	
	public static void main(String[] args) throws Exception {
		SpringApplication.run(MainClass.class, args);
	}
	
}
