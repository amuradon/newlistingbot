package cz.amuradon.tralon.newlisting.trader;

import java.util.Map;

import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestQuery;

import io.quarkus.rest.client.reactive.ClientQueryParam;
import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@ClientHeaderParam(name = "Content-Type", value = "application/json")
@RegisterRestClient(configKey = "mexc-api")
public interface MexcClient {

	@Path("/exchangeInfo")
	@GET
	String exchangeInfo();

	@Path("/depth")
	@GET
	@ClientQueryParam(name = "limit", value = "5000")
	String depth(@RestQuery String symbol);
	
	// FIXME #29 presunout blocking vys, nefunguje
	@Blocking
	@Path("/order")
	@POST
	@ClientHeaderParam(name = "X-MEXC-APIKEY", value = "${mexc.apiKey}")
	OrderResponse newOrder(@RestQuery Map<String, String> queryParams);
	
	// FIXME #29 presunout blocking vys, nefunguje
	@Blocking
	@Path("/order")
	@DELETE
	@ClientHeaderParam(name = "X-MEXC-APIKEY", value = "${mexc.apiKey}")
	OrderResponse cancelOrder(@RestQuery Map<String, String> queryParams);

	@Path("/userDataStream")
	@POST
	@ClientHeaderParam(name = "X-MEXC-APIKEY", value = "${mexc.apiKey}")
	ListenKey userDataStream(@RestQuery Map<String, String> queryParams);
}
