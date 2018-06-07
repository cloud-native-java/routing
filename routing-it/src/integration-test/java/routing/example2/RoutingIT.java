package routing.example2;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.*;
import org.cloudfoundry.operations.routes.DeleteOrphanedRoutesRequest;
import org.cloudfoundry.operations.routes.ListRoutesRequest;
import org.cloudfoundry.operations.routes.Route;
import org.cloudfoundry.operations.services.BindRouteServiceInstanceRequest;
import org.cloudfoundry.operations.services.CreateUserProvidedServiceInstanceRequest;
import org.cloudfoundry.operations.services.DeleteServiceInstanceRequest;
import org.cloudfoundry.operations.services.UnbindRouteServiceInstanceRequest;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootApplication
public class RoutingIT {

		private final Log log = LogFactory.getLog(getClass());

		private final File root = new File(".");
		private final File routingEurekaServiceManifest = new File(this.root, "routing-eureka-service/manifest.yml");
		private final File downstreamServiceManifest = new File(this.root, "downstream-service/manifest.yml");
		private final File routeServiceManifest = new File(this.root, "route-service/manifest.yml");

		private final Map<File, ApplicationManifest> manifests;
		private final Map<File, String> applicationNames;

		private final CloudFoundryOperations cloudfoundry;
		private final CloudFoundryService cloudFoundryService;

		public RoutingIT(CloudFoundryService cloudFoundryService, CloudFoundryOperations cloudfoundry) {
				this.cloudFoundryService = cloudFoundryService;

				this.manifests = Stream
					.of(this.downstreamServiceManifest, this.routeServiceManifest, this.routingEurekaServiceManifest)
					.collect(Collectors.toConcurrentMap(x -> x, x -> cloudFoundryService.getManifestFor(x.toPath())));

				this.cloudfoundry = cloudfoundry;

				this.applicationNames = this.manifests
					.entrySet()
					.stream()
					.collect(
						Collectors.toConcurrentMap(Map.Entry::getKey, x -> x.getValue().getName()));
		}

		private void error(String msg, Throwable throwable) {
				log.warn("oh the humanity! " + msg);
				if (null != throwable) {
						log.error(throwable.getMessage());
				}
		}

		@Bean
		ApplicationRunner runner() {
				return args -> {

						// unbind route service if it exists
						String routeServiceAppName = this.manifests.get(this.routeServiceManifest).getName();
						String downstreamServiceAppName = this.manifests.get(this.downstreamServiceManifest).getName();

						Flux<String> unbindRouteService = cloudFoundryService
							.findRoutesForApplication(downstreamServiceAppName)
							.flatMap(route ->
								cloudfoundry
									.services()
									.unbindRoute(UnbindRouteServiceInstanceRequest
										.builder()
										.serviceInstanceName(routeServiceAppName)
										.domainName(route.getDomain())
										.hostname(route.getHost())
										.path(route.getPath())
										.build())
									.thenMany(Mono.just(routeServiceAppName))
							)
							.doOnError(ex -> error("something went wrong in unbinding the route service " + routeServiceAppName, ex))
							.doOnComplete(() -> log.info("unbound route service " + routeServiceAppName));

						Flux<String> deleteApplications = Flux
							.just(this.downstreamServiceManifest, this.routeServiceManifest, this.routingEurekaServiceManifest)
							.map(this.applicationNames::get)
							.flatMap(name -> cloudfoundry
								.applications()
								.delete(DeleteApplicationRequest.builder().name(name).build())
								.onErrorResume(IllegalArgumentException.class, ex -> {
										error(String.format("can't delete application %s", name), ex);
										return Mono.empty();
								})
								.then(Mono.just(name)))
							.doOnComplete(() -> Stream
								.of(this.downstreamServiceManifest, this.routeServiceManifest, this.routingEurekaServiceManifest)
								.map(this.manifests::get)
								.forEach(m -> log.info("deleted application " + m.getName())));

						Flux<String> deleteServices = Flux
							.just(this.routingEurekaServiceManifest, this.routeServiceManifest)
							.map(this.applicationNames::get)
							.flatMap(name -> cloudfoundry
								.services()
								.deleteInstance(DeleteServiceInstanceRequest.builder().name(name).build())
								.onErrorResume(IllegalArgumentException.class, ex -> {
										error(String.format("can't delete service %s", name), ex);
										return Mono.empty();
								})
								.then(Mono.just(name)))
							.doOnComplete(() -> Stream
								.of(this.routingEurekaServiceManifest)
								.map(this.manifests::get)
								.forEach(m -> log.info("deleted service " + m.getName())));

						Flux<String> pushAndCreateEurekaBackingService = Flux
							.just((this.routingEurekaServiceManifest))
							.map(this.manifests::get)
							.flatMap(cloudFoundryService::pushApplication)
							.flatMap(cloudFoundryService::createBackingService)
							.doOnComplete(() -> Stream.of(this.routingEurekaServiceManifest)
								.map(this.manifests::get)
								.forEach(m -> log.info("pushed and created backing service for " + m.getName())));

						Flux<String> pushApplications = Flux
							.just(this.routeServiceManifest, this.downstreamServiceManifest)
							.map(this.manifests::get)
							.flatMap(cloudFoundryService::pushApplication)
							.doOnComplete(() -> Stream
								.of(this.routeServiceManifest, this.downstreamServiceManifest)
								.map(this.manifests::get)
								.forEach(m -> log.info("pushed " + m.getName()))
							);

						Flux<String> createBackingRouteService = cloudFoundryService
							.urlForApplication(routeServiceAppName, true)
							.flatMapMany(url ->
								cloudfoundry
									.services()
									.createUserProvidedInstance(
										CreateUserProvidedServiceInstanceRequest
											.builder()
											.name(routeServiceAppName)
											.routeServiceUrl(url)
											.build()
									)
									.thenMany(Mono.just(routeServiceAppName))
							)
							.doOnError(ex -> error("something went wrong in creating the backing route service " + routeServiceAppName, ex))
							.doOnComplete(() -> log.info("CUPS for " + routeServiceAppName));

						Flux<String> bindRouteService = cloudFoundryService
							.findRoutesForApplication(downstreamServiceAppName)
							.flatMap(route ->
								cloudfoundry
									.services()
									.bindRoute(BindRouteServiceInstanceRequest
										.builder()
										.serviceInstanceName(routeServiceAppName)
										.domainName(route.getDomain())
										.hostname(route.getHost())
										.path(route.getPath())
										.build())
									.thenMany(Mono.just(routeServiceAppName))
							)
							.doOnError(ex -> error("something went wrong in binding the route service " + routeServiceAppName, ex))
							.doOnComplete(() -> log.info("bound route service " + routeServiceAppName));

						Mono<Boolean> deleteOrphanedRoutes =
							cloudfoundry
								.routes()
								.deleteOrphanedRoutes(DeleteOrphanedRoutesRequest.builder().build())
								.then(Mono.just(true));

						Flux
							.from(unbindRouteService)
							.thenMany(deleteApplications)
							.thenMany(deleteOrphanedRoutes)
							.thenMany(deleteServices)
							.thenMany(pushAndCreateEurekaBackingService)
							.thenMany(pushApplications)
							.thenMany(createBackingRouteService)
							.thenMany(bindRouteService)
							.doOnComplete(() -> log.info("..done!"))
							.doOnError(e -> error("could not reset and deploy the applications and services", e))
							.subscribe();

						Thread.sleep(Duration.ofMinutes(5).toMillis());
				};
		}

		public static void main(String args[]) {
				SpringApplication.run(RoutingIT.class, args);
		}
}

@Service
class CloudFoundryService {

		private final CloudFoundryOperations cf;

		CloudFoundryService(CloudFoundryOperations cf) {
				this.cf = cf;
		}

		public Flux<Route> findRoutesForApplication(String appName) {
				return cf
					.routes()
					.list(ListRoutesRequest.builder().build())
					.filter(r -> r.getApplications().contains(appName));
		}

		public Mono<String> pushApplication(ApplicationManifest manifest) {
				String appName = getNameForManifest(manifest);
				return cf
					.applications()
					.pushManifest(PushApplicationManifestRequest.builder().manifest(manifest).build())
					.then(Mono.just(appName));
		}

		public Mono<String> createBackingService(String applicationNameMono) {
				return Mono.just(applicationNameMono).flatMap(
					appName -> cf
						.applications()
						.get(GetApplicationRequest.builder().name(appName).build())
						.flatMap(x -> urlForApplication(appName, false))
						.map(url -> Collections.singletonMap("uri", url))
						.flatMap(
							credentials -> cf.services()
								.createUserProvidedInstance(CreateUserProvidedServiceInstanceRequest.builder().name(appName).credentials(credentials).build())).then(Mono.just(appName)));
		}

		public Mono<String> urlForApplication(String appName, boolean https) {
				return cf
					.applications()
					.get(GetApplicationRequest.builder().name(appName).build())
					.map(ad -> ad.getUrls().iterator().next())
					.map(url -> "http" + (https ? "s" : "") + "://" + url);
		}

		public String getNameForManifest(File f) {
				return getNameForManifest(f.toPath());
		}

		public ApplicationManifest getManifestFor(Path p) {
				return ApplicationManifestUtils.read(p).iterator().next();
		}

		public String getNameForManifest(Path p) {
				return getNameForManifest(getManifestFor(p));
		}

		public String getNameForManifest(ApplicationManifest applicationManifest) {
				return applicationManifest.getName();
		}

		public Mono<String> pushApplicationAndCreateBackingService(ApplicationManifest manifest) {
				return pushApplication(manifest).flatMap(this::createBackingService);
		}
}