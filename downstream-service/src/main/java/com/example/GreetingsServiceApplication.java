package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

// <1>
@EnableDiscoveryClient
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
		Map<String, String> hi(
			@PathVariable String name,
			@RequestHeader(value = "X-CNJ-Name", required = false) Optional<String> cn) {
				String resolvedName = cn.orElse(name);
				return Collections.singletonMap("greeting", "Hello, " + resolvedName + "!");
		}
}