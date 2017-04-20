package routing;

import cnj.CloudFoundryService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.ApplicationManifest;
import org.cloudfoundry.operations.routes.ListRoutesRequest;
import org.cloudfoundry.operations.routes.Route;
import org.cloudfoundry.operations.services.BindRouteServiceInstanceRequest;
import org.cloudfoundry.operations.services.CreateUserProvidedServiceInstanceRequest;
import org.cloudfoundry.operations.services.UnbindRouteServiceInstanceRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import java.io.File;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = RoutingIT.Config.class)
public class RoutingIT {

    @SpringBootApplication
    public static class Config {
    }

    private Log log = LogFactory.getLog(getClass());

    private ClassPathResource script = new ClassPathResource(
            "/unbind_route_service_hack.sh");

    private File downstreamApplicationManifest, eurekaServiceManifest,
            routeServiceManifest;

    private String downstreamServiceId = "downstream-service",
            routeServiceId = "route-service";

    @Autowired
    private CloudFoundryOperations cloudFoundryOperations;

    @Autowired
    private CloudFoundryService cf;

    @Before
    public void before() throws Throwable {

        File root = new File(".");

        this.downstreamApplicationManifest = new File(root,
                "../downstream-service/manifest.yml");
        Assert.assertTrue("the Downstream Service manifes should exist.",
                this.downstreamApplicationManifest.exists());

        this.eurekaServiceManifest = new File(root,
                "../routing-eureka-service/manifest.yml");
        Assert.assertTrue("the Eureka service registry should exist.",
                eurekaServiceManifest.exists());

        this.routeServiceManifest = new File("../route-service/manifest.yml");
        Assert.assertTrue("the Route Service manifest should exist.",
                this.routeServiceManifest.exists());

        this.reset();
    }

    @Test
    public void routeService() throws Throwable {
        this.deployEureka();
        this.deployDownstreamApplication();
        this.deployRouteServiceApplication();
        this.applyRouteServiceTo(routeServiceId, downstreamServiceId);
        RestTemplate restTemplate = new RestTemplateBuilder().build();
        ResponseEntity<String> responseEntity = restTemplate.getForEntity(
                this.cf.urlForApplication(downstreamServiceId) + "/hi/world", String.class);
        String body = responseEntity.getBody();
        log.info("response: " + body);
        Assert.assertTrue("the request should have been intercepted", body
                .toLowerCase().contains("Cloud Natives".toLowerCase()));
    }

    private void reset() throws Throwable {

        this.runUnbindRouteServiceFor(downstreamServiceId);

        String[] apps = "route-service,downstream-service".split(",");
        for (String a : apps) {
            log.info("app: " + a);
            this.cf.destroyApplicationIfExists(a);
        }

        String[] svcs = "route-service-svc,routing-eureka-service".split(",");
        for (String s : svcs) {
            log.info("service: " + s);
            this.cf.destroyServiceIfExists(s);
        }

    }

    private void runUnbindRouteServiceFor(String serviceId) throws Throwable {
        Route downstreamAppRoute = this.forApplicationName(serviceId);
        if (null != downstreamAppRoute && cf.applicationExists(serviceId)){
            this.cloudFoundryOperations.services()
                    .unbindRoute(UnbindRouteServiceInstanceRequest
                            .builder()
                            .domainName(downstreamAppRoute.getDomain())
                            .serviceInstanceName("route-service-svc")
                            .hostname(downstreamAppRoute.getHost())
                            .build())
                    .block();
        }
    }

    private void deployEureka() throws Throwable {
        cf
                .applicationManifestFrom(this.eurekaServiceManifest)
                .entrySet()
                .stream()
                .map(
                        e -> {
                            ApplicationManifest manifest = e.getValue();
                            String appName = manifest.getName();
                            File manifestFile = e.getKey();
                            this.cf.pushApplicationAndCreateUserDefinedServiceUsingManifest(
                                    manifestFile, manifest);
                            return appName;
                        })
                .findAny()
                .orElseThrow(
                        () -> new RuntimeException(
                                "couldn't deploy the Eureka application instance!"));
    }

    private void deployRouteServiceApplication() {
        cf.applicationManifestFrom(this.routeServiceManifest).entrySet().stream()
                .map((e) -> {
                    ApplicationManifest am = e.getValue();
                    this.cf.pushApplicationUsingManifest(this.routeServiceManifest);
                    return am.getName();
                }).findAny()
                .orElseThrow(() -> new RuntimeException("can't deploy the route service!"));
    }

    private void deployDownstreamApplication() {
        cf
                .applicationManifestFrom(this.downstreamApplicationManifest)
                .entrySet()
                .stream()
                .map(e -> {
                    File f = e.getKey();
                    ApplicationManifest am = e.getValue();
                    this.cf.pushApplicationUsingManifest(f, am, true);
                    return am.getName();
                })
                .findAny()
                .orElseThrow(
                        () -> new RuntimeException("couldn't deploy the downstream application!"));
    }

    private void applyRouteServiceTo(String routeServiceId,
                                     String downstreamApplicationId) {
        log.info("binding route service for routeServiceId: " + routeServiceId
                + " and downstreamApplicationId: " + downstreamApplicationId);
        String url = cf.urlForApplication(routeServiceId);
        if (url.contains("://")) {
            url = "https://" + url.split("://")[1];
        }
        String routeServiceIdCups = routeServiceId + "-svc";
        this.cloudFoundryOperations
                .services()
                .createUserProvidedInstance(
                        CreateUserProvidedServiceInstanceRequest.builder().name(routeServiceIdCups)
                                .routeServiceUrl(url).build()).block();
        Route first = forApplicationName(downstreamApplicationId);
        log.info("trying to apply route service with: " + "domain: "
                + first.getDomain() + ", path: " + first.getPath() + ", hostname: "
                + first.getHost() + ", routeServiceInstanceId: " + routeServiceIdCups);
        this.cloudFoundryOperations
                .services()
                .bindRoute(
                        BindRouteServiceInstanceRequest.builder()
                                .serviceInstanceName(routeServiceIdCups).domainName(first.getDomain())
                                .hostname(first.getHost()).path(first.getPath()).build()).block();
        this.log.info("done!");
    }

    private Route forApplicationName(String appName) {
        return this.cloudFoundryOperations.routes()
                .list(ListRoutesRequest.builder().build())
                .filter(r -> r.getApplications().contains(appName)).blockFirst();
    }
}
