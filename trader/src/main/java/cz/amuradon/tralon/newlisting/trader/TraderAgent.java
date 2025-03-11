package cz.amuradon.tralon.newlisting.trader;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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

	private static final String HMAC_SHA256 = "HmacSHA256";

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
    
	private Mac mac;
	
	private BigDecimal price;
	
	private String buyOrderId;

	@Inject
    public TraderAgent(
    		@RestClient final MexcClient mexcClient,
    		final MexcWsClient mexcWsClient,
    		final ComputeInitialPrice computeInitialPrice,
    		@ConfigProperty(name = "mexc.secretKey") final String secretKey,
    		@ConfigProperty(name = "usdtVolume") final String usdtVolume,
    		@Named(BeanConfig.SYMBOL) final String symbol,
    		@ConfigProperty(name = TIME_PROP_NAME) final String time,
    		@Named(BeanConfig.DATA_DIR) final Path dataDir) {
    	
		scheduler = Executors.newScheduledThreadPool(2);
		this.mexcClient = mexcClient;
		this.mexcWsClient = mexcWsClient;
		this.computeInitialPrice = computeInitialPrice;
    	this.usdtVolume = new BigDecimal(usdtVolume);
    	this.symbol = symbol;
    	this.dataDir = dataDir;
    	this.mapper = new ObjectMapper();
    	
    	String[] timeParts = time.split(":");
    	if (timeParts.length >= 2) {
    		listingHour = Integer.parseInt(timeParts[0]);
    		listingMinute = Integer.parseInt(timeParts[1]);
    	} else {
    		throw new IllegalArgumentException(
    				String.format("The property '%s' has invalid value '%s'. The expected format is HH:mm",
    						TIME_PROP_NAME, time));
    	}
    	
		try {
			mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(secretKey.getBytes(), HMAC_SHA256));
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("Could not setup encoder", e);
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
		
		ListenKey listenKey = mexcClient.userDataStream(signQueryParams(queryParams));
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
			
			price = computeInitialPrice.execute(symbol, exchangeInfo, orderBook);
			Log.infof("Computed buy limit order price: %s", price);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("JSON could not be parsed", e);
		}
		
		// XXX temporary testing
		placeNewBuyOrder();
	}
	
    private Map<String, String> signQueryParams(Map<String, String> params) {
    	StringJoiner joiner = new StringJoiner("&");
    	for (Entry<String, String> entry : params.entrySet()) {
			joiner.add(entry.getKey() + "=" + entry.getValue());
		}
    	String signature = HexFormat.of().formatHex(mac.doFinal(joiner.toString().getBytes()));
    	params.put("signature", signature);
    	return params;
    }
    
	private void placeNewBuyOrder() {
		// TODO kdyz price jeste neni nasetovana metodou vyse - muze se stat, je to async
	
		Map<String, String> queryParams = new LinkedHashMap<>();
		queryParams.put("symbol", symbol);
		queryParams.put("side", "BUY");
		queryParams.put("type", "LIMIT");
		queryParams.put("quantity", usdtVolume.divide(price, 2, RoundingMode.HALF_UP).toPlainString());
		queryParams.put("price", price.toPlainString());
		
		// XXX Temporary testing
		queryParams.put("timestamp", String.valueOf(new Date().getTime()));
//		LocalDateTime now = LocalDateTime.now();
//		queryParams.put("timestamp", String.valueOf(LocalDateTime.of(now.getYear(), now.getMonthValue(),
//				now.getDayOfMonth(), listingHour, listingMinute)
//				.atZone(ZoneOffset.systemDefault()).toInstant().toEpochMilli()));
		
		// XXX Remove or debug?
		Log.infof("Query string: %s", queryParams);
		
		// TODO reagovat na ruzne chyby, napr. too many requests, nepovoleni buy order s vyssi cenou nez napr. 0.5
		for (int i = 1; i <= 10; i++) {
			Log.infof("Place new buy limit order attempt %d", i);
			try {
				OrderResponse response = mexcClient.newOrder(signQueryParams(queryParams));
				buyOrderId = response.orderId();
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
