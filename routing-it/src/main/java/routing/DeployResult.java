package routing;


import lombok.Data;
import org.reactivestreams.Publisher;

@Data
class DeployResult {

		private final Publisher<Boolean> results;
		private final String routeServiceName, routingEurekaServiceName, downstreamServiceAppName;

		public DeployResult(String routeServiceName, String downstreamServiceAppName,
																						String routingEurekaServiceName,
																						Publisher<Boolean> results) {
				this.routeServiceName = routeServiceName;
				this.routingEurekaServiceName = routingEurekaServiceName;
				this.downstreamServiceAppName = downstreamServiceAppName;
				this.results = results;
		}


}