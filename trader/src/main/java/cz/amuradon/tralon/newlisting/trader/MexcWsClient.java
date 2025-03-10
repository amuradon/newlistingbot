package cz.amuradon.tralon.newlisting.trader;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
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
	
	private static final String SPOT_TRADE_UPDATES_CHANNEL_PREFIX = "spot@public.deals.v3.api@";

	private static final String SPOT_DEPTH_UPDATES_CHANNEL_PREFIX = "spot@public.increase.depth.v3.api@";

	private static final String SPOT_ACCOUNT_UPDATES_CHANNEL = "spot@private.account.v3.api";

	private static final String SPOT_ORDER_UPDATES_CHANNEL = "spot@private.orders.v3.api";

	private final String baseUri;
	
	private final ObjectMapper mapper;
	
	private final String tradeUpdatesChannel;

	private final String depthUpdatesChannel;
	
	private final Path tradeUpdatesFilePath;
	
	private final Path depthUpdatesFilePath; 
	
	@Inject
	public MexcWsClient(@ConfigProperty(name = "mexc-api.websocket.url") final String baseUri,
			@Named(BeanConfig.SYMBOL) final String symbol,
			@Named(BeanConfig.DATA_DIR) Path dataDir) {
		this.baseUri = baseUri;
		mapper = new ObjectMapper();
		tradeUpdatesChannel = SPOT_TRADE_UPDATES_CHANNEL_PREFIX + symbol;
		depthUpdatesChannel = SPOT_DEPTH_UPDATES_CHANNEL_PREFIX + symbol;
		
		tradeUpdatesFilePath = dataDir.resolve("trades.json");
		depthUpdatesFilePath = dataDir.resolve("orderBookUpdates.json");
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
		try {
			JsonNode tree = mapper.readTree(message);
			JsonNode channelNode = tree.get("c");
			if (channelNode != null) {
				String channel = channelNode.asText();
				if (depthUpdatesChannel.equalsIgnoreCase(channel)) {
					Files.writeString(depthUpdatesFilePath, message + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				} else if (tradeUpdatesChannel.equalsIgnoreCase(channel)) {
					Files.writeString(tradeUpdatesFilePath, message + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				} else if (SPOT_ACCOUNT_UPDATES_CHANNEL.equalsIgnoreCase(channel)) {
					Log.infof("ACCOUNT: %s", message);
				} else if (SPOT_ORDER_UPDATES_CHANNEL.equalsIgnoreCase(channel)) {
					Log.infof("ORDERS: %s", message);
				}
			}
		} catch (JsonProcessingException e) {
			Log.error("The Websocket client could not parse JSON.", e);
		} catch (IOException e) {
			Log.error("The Websocket message processing failed.", e);
		}
	}
	
}
