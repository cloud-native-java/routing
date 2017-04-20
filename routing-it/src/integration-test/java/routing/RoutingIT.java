package routing;

import cnj.CloudFoundryService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cloudfoundry.client.v2.ClientV2Exception;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.ApplicationManifest;
import org.cloudfoundry.operations.routes.ListRoutesRequest;
import org.cloudfoundry.operations.routes.Route;
import org.cloudfoundry.operations.services.BindRouteServiceInstanceRequest;
import org.cloudfoundry.operations.services.CreateUserProvidedServiceInstanceRequest;
import org.cloudfoundry.operations.services.UnbindRouteServiceInstanceRequest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.stream.Stream;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = RoutingIT.Config.class)
public class RoutingIT {

 @SpringBootApplication
 public static class Config {
 }

 private Log log = LogFactory.getLog(getClass());

 private File downstreamApplicationManifest, eurekaServiceManifest,
  routeServiceManifest;

 private String eurekaServiceId = "routing-eureka-service",
  downstreamServiceId = "downstream-service", routeServiceId = "route-service";

 private String routeServiceIdCups = routeServiceId + "-svc";

 @Autowired
 private CloudFoundryOperations cloudFoundryOperations;

 private void applyRouteService() {
  log.info("binding route service for routeServiceId: " + routeServiceId
   + " and downstreamApplicationId: " + downstreamServiceId);
  String url = cf.urlForApplication(routeServiceId);
  if (url.contains("://")) {
   url = "https://" + url.split("://")[1];
  }

  this.cloudFoundryOperations
   .services()
   .createUserProvidedInstance(
    CreateUserProvidedServiceInstanceRequest.builder().name(routeServiceIdCups)
     .routeServiceUrl(url).build()).block();
  Route first = forApplicationName(downstreamServiceId);

  Assert.assertNotNull("there must be a route assigned to the service ID "
   + downstreamServiceId, first);

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

 private void deleteApps() throws Throwable {
  Stream
   .of(this.routeServiceId, this.downstreamServiceId, this.eurekaServiceId)
   .forEach(a -> {
    log.info("app: " + a);
    this.cf.destroyApplicationIfExists(a);
   });
 }

 private void reset() throws Throwable {

  // delete apps

  // unbind route service

  /*
   * Route downstreamAppRoute =
   * this.forApplicationName
   * (this.routeServiceId); if (null !=
   * downstreamAppRoute &&
   * cf.applicationExists
   * (this.downstreamServiceId)) { try {
   * log.info(String.format(
   * "attempting to unbind route service for host %s and domain %s and service-instance %s"
   * , downstreamAppRoute.getHost(),
   * downstreamAppRoute.getDomain(),
   * routeServiceId));
   * 
   * cloudFoundryOperations.services()
   * .unbindRoute
   * (UnbindRouteServiceInstanceRequest
   * .builder()
   * .serviceInstanceName(routeServiceIdCups
   * ).domainName(downstreamAppRoute.
   * getDomain())
   * .hostname(downstreamAppRoute
   * .getHost
   * ()).path(downstreamAppRoute.
   * getPath()).build()) .block(); }
   * catch (Throwable t) {
   * log.warn("oops!", t); }
   * 
   * }
   */

  // ///////////////////////////

  // cf.destroyServiceIfExists(routeServiceIdCups);

  Route first = forApplicationName(downstreamServiceId);

  // deleteApps();

  if (null != first) {

   try {
    log.info("trying to unbind route service with: " + "domain: "
     + first.getDomain() + ", path: " + first.getPath() + ", hostname: "
     + first.getHost() + ", routeServiceInstanceId: " + routeServiceIdCups);

    this.cloudFoundryOperations
     .services()
     .unbindRoute(
      UnbindRouteServiceInstanceRequest.builder()
       .serviceInstanceName(routeServiceIdCups).domainName(first.getDomain())
       .hostname(first.getHost()).path(first.getPath()).build()).block();
   }
   catch (Exception e) {
    this.log.warn(NestedExceptionUtils.buildMessage(e.getMessage(), e));
   }
   this.log.info("done trying to unbind..");
  }

  // //////////////////////////

  // delete orphaned routes
  cf.destroyOrphanedRoutes();

  this.deleteApps();

  // delete services
  String[] svcs = "route-service-svc,routing-eureka-service".split(",");
  for (String s : svcs) {
   log.info("service: " + s);
   try {
    this.cf.destroyServiceIfExists(s);
   }
   catch (ClientV2Exception e) {
    this.log.info("exception: '" + e.getDescription()
     + "' on attempting to delete " + s);
   }
  }
 }

 @Autowired
 private CloudFoundryService cf;

 @Before
 public void before() throws Throwable {
  File root = new File(".");
  this.downstreamApplicationManifest = new File(root,
   "../downstream-service/manifest.yml");
  this.eurekaServiceManifest = new File(root,
   "../routing-eureka-service/manifest.yml");
  this.routeServiceManifest = new File("../route-service/manifest.yml");

  Stream.of(this.downstreamApplicationManifest, this.eurekaServiceManifest,
   this.routeServiceManifest).forEach(
   manifest -> Assert.assertTrue(manifest.getAbsolutePath() + " must exist",
    manifest.exists()));

  this.reset();
 }

 @After
 public void after() throws Throwable {
  this.reset();
 }

 @Test
 public void routeService() throws Throwable {

  // if (true) return;

  this.deployEureka();
  this.deployDownstreamApplication();
  this.deployRouteServiceApplication();
  this.applyRouteService();
  RestTemplate restTemplate = new RestTemplateBuilder().build();
  ResponseEntity<String> responseEntity = restTemplate.getForEntity(
   this.cf.urlForApplication(downstreamServiceId) + "/hi/world", String.class);
  String body = responseEntity.getBody();
  log.info("response: " + body);
  Assert.assertTrue("the request should have been intercepted", body
   .toLowerCase().contains("Cloud Natives".toLowerCase()));
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

 private Route forApplicationName(String appName) {
  return this.cloudFoundryOperations.routes()
   .list(ListRoutesRequest.builder().build())
   .filter(r -> r.getApplications().contains(appName)).blockFirst();
 }
}
