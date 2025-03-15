package cz.amuradon.tralon.newlisting.trader;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cz.amuradon.tralon.newlisting.json.Side;
import cz.amuradon.tralon.newlisting.json.TradeDetail;
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
	
	private final int stopLoss;
	
	private final Integer[] takeProfits;
	
	private final String symbol;
	
	private final ObjectMapper mapper;
	
	private final String tradeUpdatesChannel;

	private final String depthUpdatesChannel;
	
	private final Path tradeUpdatesFilePath;
	
	private final Path depthUpdatesFilePath; 
	
	private final DataHolder dataHolder;
	
	private final List<PriceQuantity> priceQtys;
	
	private final RequestBuilder requestBuilder;

	private BigDecimal stopLossPrice = BigDecimal.ZERO;
	
	private boolean initialBuyValid = true;
	
	private long buyOrderPriceOverTimestamp = Long.MAX_VALUE;
	
	private BigDecimal baseQuantity;
	
	@Inject
	public MexcWsClient(@ConfigProperty(name = "mexc-api.websocket.url") final String baseUri,
			@ConfigProperty(name = "stopLoss") final int stopLoss,
			@ConfigProperty(name = "takeProfit") final String takeProfit,
			@Named(BeanConfig.SYMBOL) final String symbol,
			@Named(BeanConfig.DATA_DIR) final Path dataDir,
			final DataHolder dataHolder,
			final RequestBuilder requestBuilder) {
		this.baseUri = baseUri;
		this.stopLoss = stopLoss;
		this.takeProfits = Arrays.stream(takeProfit.split(",")).map(Integer::parseInt).toArray(Integer[]::new);
		this.symbol = symbol;
		mapper = new ObjectMapper();
		this.dataHolder = dataHolder;
		tradeUpdatesChannel = SPOT_TRADE_UPDATES_CHANNEL_PREFIX + symbol;
		depthUpdatesChannel = SPOT_DEPTH_UPDATES_CHANNEL_PREFIX + symbol;
		
		tradeUpdatesFilePath = dataDir.resolve("trades.json");
		depthUpdatesFilePath = dataDir.resolve("orderBookUpdates.json");
		
		priceQtys = new ArrayList<>();
		this.requestBuilder = requestBuilder;
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
					List<TradeDetail> trades = mapper.treeToValue(tree.get("d").get("deals"), new TypeReference<List<TradeDetail>>() {});

					for (TradeDetail trade : trades) {
						if (trade.price().compareTo(stopLossPrice) <= 0) {
						
							requestBuilder.newOrder()
								.symbol(symbol)
								.side(Side.SELL)
								.type("MARKET")
								.quantity(baseQuantity)
								.send();
						}
						
						if (initialBuyValid) {
							if (trade.price().compareTo(dataHolder.getInitialBuyPrice()) > 0) {
								buyOrderPriceOverTimestamp = Math.min(buyOrderPriceOverTimestamp, trade.timestamp());
								if (System.currentTimeMillis() - buyOrderPriceOverTimestamp > 500) {
									requestBuilder.cancelOrder(symbol, dataHolder.getBuyOrderId());
									initialBuyValid = false;
								}
							} else {
								buyOrderPriceOverTimestamp = Long.MAX_VALUE;
							}
						}
					}
					
				} else if (SPOT_ACCOUNT_UPDATES_CHANNEL.equalsIgnoreCase(channel)) {
					Log.infof("ACCOUNT: %s", message);
					// TODO #22 Place take profit for all partial fills
				} else if (SPOT_ORDER_UPDATES_CHANNEL.equalsIgnoreCase(channel)) {
					Log.infof("ORDERS: %s", message);
					OrderUpdate orderUpdate = mapper.readValue(message, OrderUpdate.class);
					// TODO #14 calculate weighted average price for stop loss and monitor trades / price to drop below
					//  -- VVV tak si rikam, jestli to nebylo zbytecny a mel jsem rovnou implementovat trailing stop
					final OrderUpdateData orderDetail = orderUpdate.data();
					
					if (orderDetail.orderId().equalsIgnoreCase(dataHolder.getBuyOrderId())){
						if (orderDetail.status() == Status.PARTIALLY_TRADED) {
							priceQtys.add(new PriceQuantity(orderDetail.averagePrice(), orderDetail.quantityBase()));
							baseQuantity = orderDetail.cumulativeQuantityBase();
						} else if (orderDetail.status() == Status.FULLY_TRADED) {
							priceQtys.add(new PriceQuantity(orderDetail.averagePrice(), orderDetail.quantityBase()));
							initialBuyValid = false;
							baseQuantity = orderDetail.cumulativeQuantityBase();
							buyOrderFinished();
						} else if (orderDetail.status() == Status.PARTIALLY_CANCELLED) {
							initialBuyValid = false;
							buyOrderFinished();
						}
					}
				}
			}
		} catch (JsonProcessingException e) {
			Log.error("The Websocket client could not parse JSON.", e);
		} catch (IOException e) {
			Log.error("The Websocket message processing failed.", e);
		}
	}

	private void buyOrderFinished() {
		BigDecimal initialPrice = getWeightedInitialBuyPrice();
		setStopLoss(initialPrice);
		placeTakeProfits(initialPrice);
	}
	
	private void placeTakeProfits(BigDecimal initialPrice) {
		
	}

	private BigDecimal getWeightedInitialBuyPrice() {
		BigDecimal totalAmountQuote = BigDecimal.ZERO;
		BigDecimal totalQuantityBase = BigDecimal.ZERO;
		for (PriceQuantity priceQty : priceQtys) {
			totalAmountQuote = totalAmountQuote.add(priceQty.price().multiply(priceQty.quantity()));
			totalQuantityBase = totalAmountQuote.add(priceQty.quantity());
		}
		return totalAmountQuote.divide(totalQuantityBase, 10, RoundingMode.HALF_UP);
	}

	private void setStopLoss(BigDecimal initialPrice) {
		BigDecimal factor = new BigDecimal(stopLoss)
				.divide(new BigDecimal(100), 10, RoundingMode.HALF_UP);
		stopLossPrice = initialPrice
				.multiply(factor).setScale(dataHolder.getPriceScale(), RoundingMode.HALF_UP);
	}

	private static record PriceQuantity(BigDecimal price, BigDecimal quantity) {
		
	}
}
