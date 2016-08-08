package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestOperations;

import java.net.URI;

@RestController
class Controller {

	static final String FORWARDED_URL = "X-CF-Forwarded-Url";

	static final String PROXY_METADATA = "X-CF-Proxy-Metadata";

	static final String PROXY_SIGNATURE = "X-CF-Proxy-Signature";

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	private final RestOperations restOperations;

	@Autowired
	Controller(RestOperations restOperations) {
		this.restOperations = restOperations;
	}

	@RequestMapping(headers = {FORWARDED_URL, PROXY_METADATA, PROXY_SIGNATURE})
	ResponseEntity<?> service(RequestEntity<byte[]> incoming) {
		this.logger.info("Incoming Request: {}", incoming);

		RequestEntity<?> outgoing = getOutgoingRequest(incoming);
		this.logger.info("Outgoing Request: {}", outgoing);

		return this.restOperations.exchange(outgoing, byte[].class);
	}

	private static RequestEntity<?> getOutgoingRequest(RequestEntity<?> incoming) {
		HttpHeaders headers = new HttpHeaders();
		headers.putAll(incoming.getHeaders());

		URI uri = headers
				.remove(FORWARDED_URL)
				.stream()
				.findFirst()
				.map(URI::create)
				.orElseThrow(
						() -> new IllegalStateException(String.format(
								"No %s header present", FORWARDED_URL)));

		return new RequestEntity<>(incoming.getBody(), headers,
				incoming.getMethod(), uri);
	}

}
