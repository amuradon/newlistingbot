package cz.amuradon.tralon.newlistingtrager;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import cz.amuradon.tralon.newlisting.json.ExchangeInfo;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/exchangeInfo")
@RegisterRestClient(configKey = "mexc-api")
public interface MexcClient {

	@GET
	ExchangeInfo exchangeInfo();
}
