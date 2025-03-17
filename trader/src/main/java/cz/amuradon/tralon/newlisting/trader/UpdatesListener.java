package cz.amuradon.tralon.newlisting.trader;

import static cz.amuradon.tralon.newlisting.trader.MexcWsClient.SPOT_ACCOUNT_UPDATES_CHANNEL;
import static cz.amuradon.tralon.newlisting.trader.MexcWsClient.SPOT_DEPTH_UPDATES_CHANNEL_PREFIX;
import static cz.amuradon.tralon.newlisting.trader.MexcWsClient.SPOT_ORDER_UPDATES_CHANNEL;
import static cz.amuradon.tralon.newlisting.trader.MexcWsClient.SPOT_TRADE_UPDATES_CHANNEL_PREFIX;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cz.amuradon.tralon.newlisting.json.Side;
import cz.amuradon.tralon.newlisting.json.TradeDetail;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;


@ApplicationScoped
public class UpdatesListener {
	
	private final int trailingStopBelow;

	private final int trailingStopDelayMs;
	
	private final int initialBuyOrderDelayMs;
	
	private final String symbol;
	
	private final ObjectMapper mapper;
	
	private final String tradeUpdatesChannel;

	private final String depthUpdatesChannel;
	
	private final Path tradeUpdatesFilePath;
	
	private final Path depthUpdatesFilePath; 
	
	private final DataHolder dataHolder;
	
	private final RequestBuilder requestBuilder;

	private boolean initialBuyValid = true;
	
	private boolean positionOpened = false;
	
	private long buyOrderPriceOverTimestamp = Long.MAX_VALUE;
	
	private BigDecimal baseQuantity;
	
	private BigDecimal maxPrice = BigDecimal.ZERO;
	private BigDecimal stopPrice = BigDecimal.ZERO;
	private long lastStopPriceDrop = Long.MAX_VALUE;
	
	@Inject
	public UpdatesListener(
			@ConfigProperty(name = "trailingStop.below") final int trailingStopBelow,
			@ConfigProperty(name = "trailingStop.delayMs") final int trailingStopDelayMs,
			@ConfigProperty(name = "initialBuyOrder.delayMs") final int initialBuyOrderDelayMs,
			@Named(BeanConfig.SYMBOL) final String symbol,
			@Named(BeanConfig.DATA_DIR) final Path dataDir,
			final DataHolder dataHolder,
			final RequestBuilder requestBuilder) {
		this.trailingStopBelow = trailingStopBelow;
		this.trailingStopDelayMs = trailingStopDelayMs;
		this.initialBuyOrderDelayMs = initialBuyOrderDelayMs;
		this.symbol = symbol;
		mapper = new ObjectMapper();
		this.dataHolder = dataHolder;
		tradeUpdatesChannel = SPOT_TRADE_UPDATES_CHANNEL_PREFIX + symbol;
		depthUpdatesChannel = SPOT_DEPTH_UPDATES_CHANNEL_PREFIX + symbol;
		
		tradeUpdatesFilePath = dataDir.resolve("trades.json");
		depthUpdatesFilePath = dataDir.resolve("orderBookUpdates.json");
		
		this.requestBuilder = requestBuilder;
	}

	// XXX #29 mel by blokovat cele zpracovani WS update, reseni nize nefunguje
	// @Blocking
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
					processTradeUpdate(tree);
				} else if (SPOT_ACCOUNT_UPDATES_CHANNEL.equalsIgnoreCase(channel)) {
					Log.infof("ACCOUNT: %s", message);
				} else if (SPOT_ORDER_UPDATES_CHANNEL.equalsIgnoreCase(channel)) {
					Log.infof("ORDERS: %s", message);
					processOrderUpdate(message);
				}
			}
		} catch (JsonProcessingException e) {
			Log.error("The Websocket client could not parse JSON.", e);
		} catch (IOException e) {
			Log.error("The Websocket message processing failed.", e);
		}
	}
	
	private void processTradeUpdate(JsonNode tree) throws JsonProcessingException {
		if (positionOpened) {
			List<TradeDetail> trades = mapper.treeToValue(tree.get("d").get("deals"), new TypeReference<List<TradeDetail>>() {});

			for (TradeDetail trade : trades) {
				BigDecimal price = trade.price();
				if (price.compareTo(maxPrice) > 0) {
					maxPrice = trade.price();
					stopPrice = maxPrice.multiply(new BigDecimal(100 - trailingStopBelow))
							.divide(new BigDecimal(100), dataHolder.getPriceScale(), RoundingMode.DOWN);
				}
				
				if (price.compareTo(stopPrice) <= 0) {
					if (trade.timestamp() - lastStopPriceDrop > trailingStopDelayMs) {
						requestBuilder.newOrder()
							.symbol(symbol)
							.side(Side.SELL)
							.type("MARKET")
							.quantity(baseQuantity)
							.send();
						positionOpened = false;
					} else {
						lastStopPriceDrop = trade.timestamp();
					}
				} else {
					lastStopPriceDrop = Long.MAX_VALUE;
				}
				
				// If there is still at least partial of initial buy order
				if (initialBuyValid) {
					if (trade.price().compareTo(dataHolder.getInitialBuyPrice()) > 0) {
						buyOrderPriceOverTimestamp = Math.min(buyOrderPriceOverTimestamp, trade.timestamp());
						if (System.currentTimeMillis() - buyOrderPriceOverTimestamp > initialBuyOrderDelayMs) {
							requestBuilder.cancelOrder(symbol, dataHolder.getBuyOrderId());
							initialBuyValid = false;
						}
					} else {
						buyOrderPriceOverTimestamp = Long.MAX_VALUE;
					}
				}
			}
		}
	}
	
	private void processOrderUpdate(String message) throws JsonMappingException, JsonProcessingException {
		OrderUpdate orderUpdate = mapper.readValue(message, OrderUpdate.class);
		final OrderUpdateData orderDetail = orderUpdate.data();
		
		if (orderDetail.clientOrderId().equalsIgnoreCase(dataHolder.getBuyClientOrderId())){
			if (orderDetail.status() == Status.PARTIALLY_TRADED) {
				positionOpened = true;
				baseQuantity = orderDetail.cumulativeQuantityBase();
			} else if (orderDetail.status() == Status.FULLY_TRADED) {
				positionOpened = true;
				initialBuyValid = false;
				baseQuantity = orderDetail.cumulativeQuantityBase();
			} else if (orderDetail.status() == Status.PARTIALLY_CANCELLED) {
				initialBuyValid = false;
			}
		 }
	}

}
