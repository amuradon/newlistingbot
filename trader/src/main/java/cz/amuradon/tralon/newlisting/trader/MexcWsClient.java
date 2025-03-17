package cz.amuradon.tralon.newlisting.trader;

import java.io.IOException;
import java.net.URI;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;

@ClientEndpoint
@ApplicationScoped
public class MexcWsClient {
	
	static final String SPOT_TRADE_UPDATES_CHANNEL_PREFIX = "spot@public.deals.v3.api@";

	static final String SPOT_DEPTH_UPDATES_CHANNEL_PREFIX = "spot@public.increase.depth.v3.api@";

	static final String SPOT_ACCOUNT_UPDATES_CHANNEL = "spot@private.account.v3.api";

	static final String SPOT_ORDER_UPDATES_CHANNEL = "spot@private.orders.v3.api";

	private final String baseUri;
	
	private final UpdatesListener updatesListener;
	
	private final String tradeUpdatesChannel;

	private final String depthUpdatesChannel;
	
	@Inject
	public MexcWsClient(@ConfigProperty(name = "mexc-api.websocket.url") final String baseUri,
			final UpdatesListener updatesListener,
			@Named(BeanConfig.SYMBOL) final String symbol) {
		this.baseUri = baseUri;
		this.updatesListener = updatesListener;
		
		tradeUpdatesChannel = SPOT_TRADE_UPDATES_CHANNEL_PREFIX + symbol;
		depthUpdatesChannel = SPOT_DEPTH_UPDATES_CHANNEL_PREFIX + symbol;
	}
	
	public void connect(String listenKey) {
		try {
			ContainerProvider.getWebSocketContainer()
				.connectToServer(this, URI.create(baseUri + "?listenKey=" + listenKey));
		} catch (DeploymentException | IOException e) {
			throw new IllegalStateException("The Websocket client could not be established.", e);
		}
	}
	
	@OnOpen
	public void open(Session session) {
		try {
			session.getBasicRemote().sendText(String.format(
					"{ \"method\":\"SUBSCRIPTION\", \"params\":[\"%s\", \"%s\", \"%s\", \"%s\"] }",
					SPOT_ACCOUNT_UPDATES_CHANNEL, SPOT_ORDER_UPDATES_CHANNEL,
					depthUpdatesChannel, tradeUpdatesChannel));
		} catch (IOException e) {
			throw new IllegalStateException("The Websocket client could not subscribe to channels.", e);
		}
	}
	
	@OnMessage
	public void onMessage(String message) {
		updatesListener.onMessage(message);
	}

}
