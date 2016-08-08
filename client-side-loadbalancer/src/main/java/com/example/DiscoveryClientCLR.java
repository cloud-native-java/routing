package com.example;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

@Component
public class DiscoveryClientCLR implements CommandLineRunner {

	private final DiscoveryClient discoveryClient;

	private Log log = LogFactory.getLog(getClass());

	// <1>
	@Autowired
	public DiscoveryClientCLR(DiscoveryClient discoveryClient) {
		this.discoveryClient = discoveryClient;
	}

	@Override
	public void run(String... args) throws Exception {

		// <2>
		this.log.info("localServiceInstance");
		this.logServiceInstance(this.discoveryClient.getLocalServiceInstance());

		// <3>
		String serviceId = "greetings-service";
		this.log.info(String.format("registered instances of '%s'", serviceId));
		this.discoveryClient.getInstances(serviceId).forEach(
				this::logServiceInstance);
	}

	private void logServiceInstance(ServiceInstance si) {
		String msg = String.format("host = %s, port = %s, service ID = %s",
				si.getHost(), si.getPort(), si.getServiceId());
		log.info(msg);
	}
}
