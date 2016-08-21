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
import org.springframework.web.client.RestTemplate;

import java.net.URI;

/**
 * @author Ben Hale
 */
@RestController
class Controller {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    //<1>
    private static final String FORWARDED_URL = "X-CF-Forwarded-Url";
    private static final String PROXY_METADATA = "X-CF-Proxy-Metadata";
    private static final String PROXY_SIGNATURE = "X-CF-Proxy-Signature";

    private final RestOperations restOperations;

    // <2>
    @RequestMapping(headers = {FORWARDED_URL, PROXY_METADATA, PROXY_SIGNATURE})
    ResponseEntity<?> service(RequestEntity<byte[]> incoming) {

        this.logger.info("incoming request: {}", incoming);

        HttpHeaders headers = new HttpHeaders();
        headers.putAll(incoming.getHeaders());

        // <3>
        URI uri = headers
                .remove(FORWARDED_URL)
                .stream()
                .findFirst()
                .map(URI::create)
                .orElseThrow(() -> new IllegalStateException(
                        String.format("No %s header present", FORWARDED_URL)));

        // <4>
        RequestEntity<?> outgoing = new RequestEntity<>(
                ((RequestEntity<?>) incoming).getBody(),
                headers,
                incoming.getMethod(),
                uri);

        this.logger.info("outgoing request: {}", outgoing);

        return this.restOperations.exchange(outgoing, byte[].class);
    }

    @Autowired
    Controller(RestTemplate restOperations) {
        this.restOperations = restOperations;
    }
}
