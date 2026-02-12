package com.example.cloud.loadbalancer;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@LoadBalancerClients({@LoadBalancerClient(name = "first", configuration = LoadBalancerConfigExample.class)})
public class LoadBalancerClientExample {

	@Bean
	@LoadBalanced
	WebClient.Builder loadBalancedWebClientBuilder() {
		return WebClient.builder();
	}

}
