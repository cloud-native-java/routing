package com.example;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

@Component
class DiscoveryClientRunner implements ApplicationRunner {

 private final DiscoveryClient discoveryClient;

 private Log log = LogFactory.getLog(getClass());

 // <1>
 DiscoveryClientRunner(DiscoveryClient discoveryClient) {
  this.discoveryClient = discoveryClient;
 }

 private void logServiceInstance(ServiceInstance si) {
  String msg = String.format("host = %s, port = %s, service ID = %s",
   si.getHost(), si.getPort(), si.getServiceId());
  log.info(msg);
 }

 @Override
 public void run(ApplicationArguments args) throws Exception {
  // <2>
  String serviceId = "greetings-service";
  this.log.info(String.format("registered instances of '%s'", serviceId));
  this.discoveryClient.getInstances(serviceId)
   .forEach(this::logServiceInstance);
 }
}
