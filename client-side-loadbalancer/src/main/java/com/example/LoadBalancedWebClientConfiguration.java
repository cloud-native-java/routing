package com.example;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class LoadBalancedWebClientConfiguration {

 // <1>
 @Bean
 @LoadBalanced
 WebClient webClient() {
  return WebClient.builder().build();
 }
}
