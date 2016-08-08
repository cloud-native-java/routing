package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@EnableDiscoveryClient
// <1>
@SpringBootApplication
public class GreetingsServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(GreetingsServiceApplication.class, args);
	}
}

@RestController
class GreetingsRestController {

	// <2>
	@RequestMapping(method = RequestMethod.GET, value = "/hi/{name}")
	Map<String, String> hi(@PathVariable String name) {
		return Collections.singletonMap("greeting", "Hello, " + name + "!");
	}
}