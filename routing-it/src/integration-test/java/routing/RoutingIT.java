package routing;

import cnj.CloudFoundryService;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.function.Function;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = RoutingIT.Config.class)
public class RoutingIT {

		@SpringBootApplication
		public static class Config {

				@Bean
				RouteServiceDeployer routeServiceDeployer(
					CloudFoundryService cfs,
					CloudFoundryOperations cops) {
						return new RouteServiceDeployer(cfs, cops);
				}
		}

		@Autowired
		private RouteServiceDeployer routeServiceDeployer;

		@Autowired
		private CloudFoundryOperations cloudFoundryOperations;

		@Test
		public void deploy() {

				Function<String, Mono<Boolean>> appExists = input -> this.cloudFoundryOperations.applications().list().filter(si -> si.getName().equals(input)).hasElements();
				Function<String, Mono<Boolean>> svcExists = input -> this.cloudFoundryOperations.services().listInstances().filter(si -> si.getName().equals(input)).hasElements();

				DeployResult deployResult = this.routeServiceDeployer.deploy();

				Publisher<Boolean> just = Flux
					.just(
						appExists.apply(deployResult.getDownstreamServiceAppName()),
						svcExists.apply(deployResult.getRouteServiceName()),
						svcExists.apply(deployResult.getRoutingEurekaServiceName()))
					.flatMap(m -> m.flatMap(Mono::just));

				Flux<Boolean> results = Flux
					.from(deployResult.getResults())
					.thenMany(just);

				StepVerifier
					.create(results)
					.expectNextMatches(x -> x)
					.expectNextMatches(x -> x)
					.expectNextMatches(x -> x)
					.verifyComplete();
		}
}