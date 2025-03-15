package cz.amuradon.tralon.newlisting.trader;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cz.amuradon.tralon.newlisting.json.ExchangeInfo;
import cz.amuradon.tralon.newlisting.json.Side;
import cz.amuradon.tralon.newlisting.json.SymbolInfo;
import cz.amuradon.tralon.newlisting.trader.RequestBuilder.SignedNewOrderRequestBuilder;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.WebApplicationException;

@ApplicationScoped
public class TraderAgent {
	
	private static final String NOT_YET_TRADING_ERR = "symbol not support api";

	private static final String TIME_PROP_NAME = "time";

	private final ScheduledExecutorService scheduler;
	
	private final MexcClient mexcClient;
	
	private final MexcWsClient mexcWsClient;
	
	private final ComputeInitialPrice computeInitialPrice;
	
    private final BigDecimal usdtVolume;
    private final String symbol;
    private final ObjectMapper mapper;
    
    private final Path dataDir;
    
    private final int listingHour;
    
    private final int listingMinute;
    
    private final DataHolder dataHolder;
    
	private final RequestBuilder requestBuilder; 
	
	@Inject
    public TraderAgent(
    		@RestClient final MexcClient mexcClient,
    		final MexcWsClient mexcWsClient,
    		final ComputeInitialPrice computeInitialPrice,
    		@ConfigProperty(name = "mexc.secretKey") final String secretKey,
    		@ConfigProperty(name = "usdtVolume") final String usdtVolume,
    		@Named(BeanConfig.SYMBOL) final String symbol,
    		@ConfigProperty(name = TIME_PROP_NAME) final String time,
    		@Named(BeanConfig.DATA_DIR) final Path dataDir,
    		final DataHolder dataHolder,
    		final RequestBuilder requestBuilder) {
    	
		scheduler = Executors.newScheduledThreadPool(2);
		this.mexcClient = mexcClient;
		this.mexcWsClient = mexcWsClient;
		this.computeInitialPrice = computeInitialPrice;
    	this.usdtVolume = new BigDecimal(usdtVolume);
    	this.symbol = symbol;
    	this.dataDir = dataDir;
    	this.mapper = new ObjectMapper();
    	this.dataHolder = dataHolder;
    	this.requestBuilder = requestBuilder;
    	
    	String[] timeParts = time.split(":");
    	if (timeParts.length >= 2) {
    		listingHour = Integer.parseInt(timeParts[0]);
    		listingMinute = Integer.parseInt(timeParts[1]);
    	} else {
    		throw new IllegalArgumentException(
    				String.format("The property '%s' has invalid value '%s'. The expected format is HH:mm",
    						TIME_PROP_NAME, time));
    	}
    }
    
	// XXX Temporary testing
//	@Startup
    public void run() {
		int startHour = listingHour;
		int startMinute = listingMinute;
    		
		if (startMinute == 0) {
			startHour--;
			startMinute = 59;
		} else {
			startMinute--;
		}

		Log.infof("Listing at %d:%d", listingHour, listingMinute);
		Log.infof("Agent starts at %d:%d", startHour, startMinute);
		
		LocalDateTime beforeStart = LocalDateTime.now().withHour(startHour).withMinute(startMinute).withSecond(50);
		Log.infof("Listing start: %s", beforeStart);
		LocalDateTime now = LocalDateTime.now();
		
		if (now.isAfter(beforeStart)) {
			throw new IllegalStateException(String.format("The start time '%s' is in past", beforeStart));
		}
		
		ScheduledFuture<?> prepareTask = scheduler.schedule(this::prepare, Math.max(0, now.until(beforeStart, ChronoUnit.SECONDS)),
				TimeUnit.SECONDS);
		ScheduledFuture<?> placeNewBuyOrderTask = scheduler.schedule(this::placeNewBuyOrder,
				Math.max(0, now.until(beforeStart.withSecond(59).withNano(800000000), ChronoUnit.MILLIS)),
				TimeUnit.MILLISECONDS);
		
		try {
			prepareTask.get();
			placeNewBuyOrderTask.get();
		} catch (Exception e) {
			throw new RuntimeException("The execution failed", e);
		}
		
	}
    
    // XXX Temporary testing
    @Startup
	public void prepare() {
		/*
		 * TODO
		 * - When application starts delete all existing listenKeys
		 *   - I can't there might be more agents running at the same time
		 * - Every 60 minutes sent keep alive request
		 * - After 24 hours reconnect - create a new listen key?
		 */
		
		// subscribe updates
		long timestamp = new Date().getTime();
		
		Map<String, String> queryParams = new LinkedHashMap<>();
		queryParams.put("timestamp", String.valueOf(timestamp));
		
		ListenKey listenKey = mexcClient.userDataStream(requestBuilder.signQueryParams(queryParams));
		mexcWsClient.connect(listenKey.listenKey());
		
		// get order book
		String exchangeInfoJson = mexcClient.exchangeInfo();
		String orderBookJson= mexcClient.depth(symbol);

		try {
			Files.writeString(dataDir.resolve("exchangeInfo.json"), exchangeInfoJson, StandardOpenOption.CREATE);
			Files.writeString(dataDir.resolve("depth.json"), orderBookJson, StandardOpenOption.CREATE);
		} catch (IOException e) {
			throw new IllegalStateException("Could write JSON to files", e);
		}
		

		try {
			ExchangeInfo exchangeInfo = mapper.readValue(exchangeInfoJson, ExchangeInfo.class);
			OrderBook orderBook = mapper.readValue(orderBookJson, OrderBook.class);
			
			dataHolder.setInitialBuyPrice(computeInitialPrice.execute(symbol, extractPriceScale(symbol, exchangeInfo), orderBook));
			Log.infof("Computed buy limit order price: %s", dataHolder.getInitialBuyPrice());
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("JSON could not be parsed", e);
		}
		
		// XXX temporary testing
		placeNewBuyOrder();
	}
    
	private int extractPriceScale(String symbol, ExchangeInfo exchangeInfo) {
		int priceScale = 4;
		for (SymbolInfo symbolInfo: exchangeInfo.symbols()) {
			if (symbolInfo.symbol().equalsIgnoreCase(symbol)) {
				priceScale = symbolInfo.quotePrecision();
				break;
			}
		}
		dataHolder.setPriceScale(priceScale);
		return priceScale;
	}
	
	private void placeNewBuyOrder() {
		// TODO kdyz price jeste neni nasetovana metodou vyse - muze se stat, je to async
	
		BigDecimal price = dataHolder.getInitialBuyPrice();
		SignedNewOrderRequestBuilder newOrderBuilder = requestBuilder.newOrder()
			.symbol(symbol)
			.side(Side.BUY)
			.type("LIMIT")
			.quantity(usdtVolume.divide(price, 2, RoundingMode.HALF_UP))
			.price(price)
		
			// XXX Temporary testing
			.timestamp(new Date().getTime())
//		LocalDateTime now = LocalDateTime.now();
//		.timestamp(LocalDateTime.of(now.getYear(), now.getMonthValue(),
//				now.getDayOfMonth(), listingHour, listingMinute)
//				.atZone(ZoneOffset.systemDefault()).toInstant().toEpochMilli())
			.signParams();
		
		// TODO muze byt az sem vsechno udelano dopredu a tady pockat na spravny cas otevreni burzy?
		
		// TODO reagovat na ruzne chyby, napr. too many requests, nepovoleni buy order s vyssi cenou nez napr. 0.5
		for (int i = 1; i <= 10; i++) {
			Log.infof("Place new buy limit order attempt %d", i);
			try {
				OrderResponse response = newOrderBuilder.send();
				dataHolder.setBuyOrderId(response.orderId());
				Log.infof("New order placed: %s", response);
				break;
			} catch (WebApplicationException e) {
				ErrorResponse errorResponse = e.getResponse().readEntity(ErrorResponse.class);
				Log.errorf("ERR response: %d - %s: %s", e.getResponse().getStatus(),
						e.getResponse().getStatusInfo().getReasonPhrase(), errorResponse);
				if (!NOT_YET_TRADING_ERR.equalsIgnoreCase(errorResponse.msg())) {
					break;
				}
			}
		}
	}

}
