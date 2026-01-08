package com.example.demo;

import java.util.LinkedHashMap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("com.example.serviceclient")
public class HttpServiceClientProperties extends LinkedHashMap<String, HttpClientProperties> {

}
