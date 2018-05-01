package routing.example2;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.*;
import org.cloudfoundry.operations.services.CreateUserProvidedServiceInstanceRequest;
import org.cloudfoundry.operations.services.DeleteServiceInstanceRequest;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
	* @author <a href="mailto:josh@joshlong.com">Josh Long</a>
	*/
@SpringBootApplication
public class Example2 {

		private final File root = new File(".");

		private final File routingEurekaServiceManifest = new File(this.root, "routing-eureka-service/manifest.yml");
		private final File downstreamServiceManifest = new File(this.root, "downstream-service/manifest.yml");
		//		private final File clientSideLoadbalancerManifest = new File(this.root, "client-side-loadbalancer/manifest.yml");
		private final File routeServiceManifest = new File(this.root, "route-service/manifest.yml");

		private final Map<File, ApplicationManifest> manifests = Stream
			.of(this.downstreamServiceManifest, this.routeServiceManifest, this.routingEurekaServiceManifest)
			.collect(Collectors.toConcurrentMap(x -> x, x -> Utils.getManifestFor(x.toPath())));

		private final Map<File, String> applicationNames = manifests
			.entrySet()
			.stream()
			.collect(Collectors.toConcurrentMap(Map.Entry::getKey, x -> x.getValue().getName()));

		private Log log = LogFactory.getLog(getClass());

		private void error(String msg, Throwable throwable) {
				if (log.isErrorEnabled()) {
						log.error(msg, throwable);
				}
				else {
						log.warn(msg);
				}
		}

		@Bean
		ApplicationRunner runner(CloudFoundryOperations cloudfoundry) {
				return args -> {

						Flux<String> deleteApplications = Flux
							.just(this.downstreamServiceManifest, this.routeServiceManifest, this.routingEurekaServiceManifest)
							.map(this.applicationNames::get)
							.flatMap(name -> cloudfoundry
								.applications().delete(DeleteApplicationRequest.builder().name(name).build())
								.onErrorResume(IllegalArgumentException.class, ex -> {
										error(String.format("can't delete application %s", name), ex);
										return Mono.empty();
								})
								.then(Mono.just(name))
							);

						Flux<String> deleteServices = Flux
							.just(this.routingEurekaServiceManifest)
							.map(this.applicationNames::get)
							.flatMap(name ->
								cloudfoundry.services()
									.deleteInstance(DeleteServiceInstanceRequest.builder().name(name).build())
									.onErrorResume(IllegalArgumentException.class, ex -> {
											error(String.format("can't delete service %s", name), ex);
											return Mono.empty();
									})
									.then(Mono.just(name))
							);

						Flux<String> pushAndCreateEurekaBackingService = Flux.just((this.routingEurekaServiceManifest))
							.map(this.manifests::get)
							.flatMap(manifest -> Utils.pushApplication(cloudfoundry, manifest))
							.flatMap(app -> Utils.createBackingService(cloudfoundry, app));

						Flux<String> pushApplications = Flux
							.just(this.routingEurekaServiceManifest, this.downstreamServiceManifest)
							.map(this.manifests::get)
							.flatMap(manifest -> Utils.pushApplication(cloudfoundry, manifest));

						Flux
							.from(deleteApplications)
							.thenMany(deleteServices)
							.thenMany(pushAndCreateEurekaBackingService)
							.thenMany(pushApplications)
							.doOnComplete(() -> log.info("..done!"))
							.doOnError(e -> error("couldn't reset and deploy the applications and services", e))
							.subscribe();

						Thread.sleep(Duration.ofMinutes(5).toMillis());
				};
		}

		public static void main(String args[]) {
				SpringApplication.run(Example2.class, args);
		}
}

/**
	*
	*/
abstract class Utils {

		public static Mono<String> pushApplication(CloudFoundryOperations cf, ApplicationManifest manifest) {
				String appName = Utils.getNameForManifest(manifest);
				return cf
					.applications()
					.pushManifest(PushApplicationManifestRequest
						.builder()
						.manifest(manifest)
						.build()
					)
					.then(Mono.just(appName));
		}

		public static Mono<String> createBackingService(CloudFoundryOperations cloudFoundryOperations, String applicationNameMono) {
				return Mono
					.just(applicationNameMono)
					.flatMap(appName -> cloudFoundryOperations
						.applications()
						.get(GetApplicationRequest.builder().name(appName).build())
						.flatMap(x -> Utils.urlForApplication(cloudFoundryOperations, appName, false))
						.map(url -> Collections.singletonMap("uri", url))
						.flatMap(credentials ->
							cloudFoundryOperations
								.services()
								.createUserProvidedInstance(CreateUserProvidedServiceInstanceRequest
									.builder()
									.name(appName)
									.credentials(credentials)
									.build()
								)
						)
						.then(Mono.just(appName)));
		}


		public static Mono<String> urlForApplication(CloudFoundryOperations cf, String appName, boolean https) {
				return cf
					.applications()
					.get(GetApplicationRequest.builder().name(appName).build())
					.map(ad -> ad.getUrls().iterator().next())
					.map(url -> "http" + (https ? "s" : "") + "://" + url);
		}

		public static String getNameForManifest(File f) {
				return getNameForManifest(f.toPath());
		}

		public static ApplicationManifest getManifestFor(Path p) {
				return ApplicationManifestUtils.read(p).iterator().next();
		}

		public static String getNameForManifest(Path p) {
				return getNameForManifest(getManifestFor(p));
		}

		public static String getNameForManifest(ApplicationManifest applicationManifest) {
				return applicationManifest.getName();
		}

		public static Mono<String> pushApplicationAndCreateBackingService(CloudFoundryOperations cf, ApplicationManifest manifest) {
				return pushApplication(cf, manifest)
					.flatMap(appName -> createBackingService(cf, appName));
		}
}