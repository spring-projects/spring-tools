package com.example.cloud.loadbalancer;

import org.springframework.context.annotation.Bean;

public class LoadBalancerConfigExample {
	
	@Bean
	BeanType specialBean() {
		return new BeanType();
	}

}
