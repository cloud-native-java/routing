package routing.example1;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.ApplicationManifest;
import org.cloudfoundry.operations.applications.ApplicationManifestUtils;
import org.cloudfoundry.operations.applications.PushApplicationManifestRequest;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
	* @author <a href="mailto:josh@joshlong.com">Josh Long</a>
	*/
@SpringBootApplication
public class Example {

		public static void main(String args[]) {
				SpringApplication.run(Example.class, args);
		}

		private final File root = new File(".");
		private final File routingEurekaServiceManifest = new File(this.root, "routing-eureka-service/manifest.yml");
		private final File downstreamServiceManifest = new File(this.root, "downstream-service/manifest.yml");
		private final File clientSideLoadbalancerManifest = new File(this.root, "client-side-loadbalancer/manifest.yml");
		private final File routeServiceManifest = new File(this.root, "route-service/manifest.yml");

		private final Map<String, File> appNameToManifestMap = Stream.of(
			this.clientSideLoadbalancerManifest, this.downstreamServiceManifest,
			this.routeServiceManifest, this.routingEurekaServiceManifest)
			.collect(Collectors.toMap(Utils::getNameForManifest, file -> file));

		@Bean
		ApplicationRunner runner(CloudFoundryOperations cloudFoundryOperations) {
				return args -> {

						Log log = LogFactory.getLog(getClass());

						Set<Path> paths = Stream.of(this.routeServiceManifest, this.routingEurekaServiceManifest)
							.map(File::toPath)
							.collect(Collectors.toSet());

						CountDownLatch latch = new CountDownLatch(paths.size());

						Flux
							.fromIterable(paths)
							.map(ApplicationManifestUtils::read)
							.map(x -> x.iterator().next())
							.flatMap(list -> {
									return cloudFoundryOperations
										.applications()
										.pushManifest(PushApplicationManifestRequest
											.builder()
											.manifest(list)
											.build()
										);
							})
							.doOnError(log::error)
							.doFinally(x -> {
									log.info("finished...");
									latch.countDown();
							})
							.subscribe(log::info);

						latch.await();

				};
		}
}

abstract class Utils {

		public static String getNameForManifest(File f) {
				return getNameForManifest(f.toPath());
		}

		public static String getNameForManifest(Path p) {
				return getNameForManifest(ApplicationManifestUtils.read(p).iterator().next());
		}

		public static String getNameForManifest(ApplicationManifest applicationManifest) {
				return applicationManifest.getName();
		}
}