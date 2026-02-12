package com.example.cloud.loadbalancer;

import org.springframework.context.annotation.Bean;

public class LoadBalancerConfigNotRegistered {
	
	@Bean
	BeanType specialBean2() {
		return new BeanType();
	}

}
