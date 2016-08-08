package com.example;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequest;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class LoadBalancerClientCLR implements CommandLineRunner {

    private final Log log = LogFactory.getLog(getClass());
    private final LoadBalancerClient loadBalancerClient;

    @Autowired
    public LoadBalancerClientCLR(LoadBalancerClient loadBalancerClient) {
        this.loadBalancerClient = loadBalancerClient;
    }

    @Override
    public void run(String... args) throws Exception {
        LoadBalancerRequest<URI> loadBalancerRequest = server -> URI
                .create("http://" + server.getHost() + ":" + server.getPort()
                        + "/");
        URI uri = this.loadBalancerClient.execute("greetings-service",
                loadBalancerRequest);
        log.info("resolved service " + uri.toString());
    }

    private URI uriFromServiceInstance(ServiceInstance server) {
        String string = String.format("http://%s:%s/",
                server.getHost(), server.getPort());
        return URI.create(string);
    }
}
