package routing.example2;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.ApplicationManifest;
import org.cloudfoundry.operations.applications.ApplicationManifestUtils;
import org.cloudfoundry.operations.applications.GetApplicationRequest;
import org.cloudfoundry.operations.applications.PushApplicationManifestRequest;
import org.cloudfoundry.operations.services.CreateUserProvidedServiceInstanceRequest;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;

/**
	* @author <a href="mailto:josh@joshlong.com">Josh Long</a>
	*/
@SpringBootApplication
public class Example2 {

		private final File root = new File(".");

		private final File routingEurekaServiceManifest = new File(this.root, "routing-eureka-service/manifest.yml");

		private final File downstreamServiceManifest = new File(this.root, "downstream-service/manifest.yml");

		private final File clientSideLoadbalancerManifest = new File(this.root, "client-side-loadbalancer/manifest.yml");

		private final File routeServiceManifest = new File(this.root, "route-service/manifest.yml");


		@Bean
		ApplicationRunner routeExample(CloudFoundryOperations cloudFoundryOperations) {
				return args -> {
						Log log = LogFactory.getLog(getClass());
						ApplicationManifest manifest = ApplicationManifestUtils
							.read(this.routingEurekaServiceManifest.toPath())
							.iterator()
							.next();
						Utils
							.pushApplicationAndCreateBackingService(cloudFoundryOperations, manifest)
							.log()
							.subscribe(appName -> log.info("pushed application '" + appName + "' and created backing service of the same name"));
						Thread.sleep(Duration.ofMinutes(5).toMillis());
				};
		}
		/*private void deployEureka() throws Throwable {
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
		}*/


		public static void main(String args[]) {
				SpringApplication.run(Example2.class, args);
		}
}


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

		public static Mono<String> createBackingService(
			CloudFoundryOperations cloudFoundryOperations, String applicationNameMono) {
				return Mono.just(applicationNameMono)
					.flatMap(appName -> cloudFoundryOperations
						.applications()
						.get(GetApplicationRequest.builder().name(appName).build())
						.map(ad -> ad.getUrls().iterator().next())
						.map(u -> Collections.singletonMap("appNameMono", u))
						.flatMap(m ->
							cloudFoundryOperations
								.services()
								.createUserProvidedInstance(CreateUserProvidedServiceInstanceRequest
									.builder()
									.name(appName)
									.credentials(m)
									.build()
								)
						)
						.then(Mono.just(appName))
					);
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

		public static String getNameForManifest(Path p) {
				return getNameForManifest(ApplicationManifestUtils.read(p).iterator().next());
		}

		public static String getNameForManifest(ApplicationManifest applicationManifest) {
				return applicationManifest.getName();
		}

		public static Mono<String> pushApplicationAndCreateBackingService(CloudFoundryOperations cf, ApplicationManifest manifest) {
				return pushApplication(cf, manifest)
						.flatMap(appName -> createBackingService(cf, appName));
		}
}