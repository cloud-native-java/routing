package com.example;

import com.netflix.loadbalancer.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class RibbonCLR implements CommandLineRunner {

    private final DiscoveryClient discoveryClient;

    private final Log log = LogFactory.getLog(getClass());

    @Autowired
    public RibbonCLR(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    @Override
    public void run(String... args) throws Exception {

        String serviceId = "greetings-service";

        // <1>
        List<Server> servers = this.discoveryClient.getInstances(serviceId)
                .stream().map(si -> new Server(si.getHost(), si.getPort()))
                .collect(Collectors.toList());

        // <2>
        IRule roundRobinRule = new RoundRobinRule();

        BaseLoadBalancer loadBalancer = LoadBalancerBuilder.newBuilder()
                .withRule(roundRobinRule)
                .buildFixedServerListLoadBalancer(servers);

        IntStream.range(0, 10).forEach(i -> {
            // <3>
            Server server = loadBalancer.chooseServer();
            URI uri = URI.create("http://" + server.getHost() + ":"
                    + server.getPort() + "/");
            log.info("resolved service " + uri.toString());
        });
    }
}
