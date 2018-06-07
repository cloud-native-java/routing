package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.Map;

@Component
class LoadBalancedWebClientRunner implements ApplicationRunner {

 private final Log log = LogFactory.getLog(getClass());

 private final WebClient client;

 LoadBalancedWebClientRunner(@LoadBalanced WebClient client) {
  this.client = client;
 }

 // <1>
 @Override
 public void run(ApplicationArguments args) throws Exception {

  Map<String, String> variables = Collections.singletonMap("name",
   "Cloud Natives!");

  // <2>
  this.client.get().uri("http://greetings-service/hi/{name}", variables)
   .retrieve().bodyToMono(JsonNode.class).map(x -> x.get("greeting").asText())
   .subscribe(greeting -> log.info("greeting: " + greeting));
 }
}
