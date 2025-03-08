package cz.amuradon.tralon.newlistingtrager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.TemplatedRouteBuilder;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.component.http.HttpConstants;
import org.apache.camel.component.http.HttpMethods;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.commons.codec.binary.Hex;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MyRouteBuilder extends EndpointRouteBuilder {
	
	private static final String TIME_PROP_NAME = "time";

	private static final String SPOT_TRADE_UPDATES_CHANNEL_PREFIX = "spot@public.deals.v3.api@";

	private static final String SPOT_DEPTH_UPDATES_CHANNEL_PREFIX = "spot@public.increase.depth.v3.api@";

	private static final String SPOT_ACCOUNT_UPDATES_CHANNEL = "spot@private.account.v3.api";

	private static final String SPOT_ORDER_UPDATES_CHANNEL = "spot@private.orders.v3.api";

	public static final String SYMBOL_HEADER_NAME = "Symbol";
	
	public static final String EXCHANGE_INFO_HEADER_NAME = "ExchangeInfo";
	
	private static final String DIRECT_PLACE_NEW_ORDER = "direct:placeNewOrder";

	private static final String DIRECT_PREPARE_BUY_ORDER_DATA = "direct:prepareBuyOrderData";

	private static final String DIRECT_SUBSCRIBE_WS_UPDATES = "direct:subscribeWsUpdates";
	
	private static final String HMAC_SHA256 = "HmacSHA256";
	private static final String ACCESS_KEY = "mx0vgl5eTwQI22AEqq";
	private static final String SECRET_KEY = "51c13954c8e043cd9215b9f32c8eaf86";

	private static final String USER_DATA_STREAM = "UserDataStream";
	
	private final String buyOrderPriceProperty;

    private final BigDecimal usdtVolume;
    private final String baseAsset;
    private final String quoteAsset;
    private final String symbol;
    private final int startHour;
    private final int startMinute;
    
	private Mac mac;
	
	private BigDecimal price;
	
	private String buyOrderId;

	@Inject
    public MyRouteBuilder(@ConfigProperty(name = "usdtVolume") final String usdtVolume,
    		@ConfigProperty(name = "baseAsset") final String baseAsset,
    		@ConfigProperty(name = "quoteAsset") final String quoteAsset,
    		@ConfigProperty(name = TIME_PROP_NAME) final String time,
    		@ConfigProperty(name = ComputeInitialPrice.BUY_ORDER_LIMIT_PRICE_PROP_NAME) final String buyOrderPriceProperty) {
    	
    	this.usdtVolume = new BigDecimal(usdtVolume);
    	this.baseAsset = baseAsset;
    	this.quoteAsset = quoteAsset;
    	this.symbol = baseAsset + quoteAsset;
    	this.buyOrderPriceProperty = buyOrderPriceProperty;
    	
    	String[] timeParts = time.split(":");
    	if (timeParts.length >= 2) {
    		int hour = Integer.parseInt(timeParts[0]);
    		int minute = Integer.parseInt(timeParts[1]);
    		
    		if (minute == 0) {
    			hour--;
    			minute = 59;
    		} else {
    			minute--;
    		}

    		startHour = hour;
    		startMinute = minute;
    		Log.infof("Start scheduled on %d:%d", startHour, startMinute);
    	} else {
    		throw new IllegalArgumentException(
    				String.format("The property '%s' has invalid value '%s'. The expected format is HH:mm",
    						TIME_PROP_NAME, time));
    	}
    	SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(), HMAC_SHA256);
    
		try {
			mac = Mac.getInstance(HMAC_SHA256);
			mac.init(secretKey);
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
	@Override
    public void configure() throws Exception {
		LocalDate localDate = LocalDate.now();
//		from("timer:placeOrder?repeatCount=1")
    	from(String.format("quartz:startListing?cron=50+%d+%d+%d+%d+?+%d",
    			startMinute, startHour,
    			localDate.getDayOfMonth(), localDate.getMonthValue(), localDate.getYear()))
			.multicast()
				.to(DIRECT_SUBSCRIBE_WS_UPDATES)
				.to(DIRECT_PREPARE_BUY_ORDER_DATA)
				;
		
		from(DIRECT_PREPARE_BUY_ORDER_DATA)
			.setHeader(SYMBOL_HEADER_NAME).constant(symbol)
			.multicast(new HttpRequestsAggregationStrategy(), true)
				.pipeline()
					.to("https://api.mexc.com/api/v3/exchangeInfo?symbol=" + symbol)
					.to("file:data/mexc/?fileName=${date:now:yyyyMMdd}/" + symbol + "/exchangeInfo.json")
					.unmarshal().json(JsonLibrary.Jackson, ExchangeInfo.class)
				.end()
				.pipeline()
					.to("https://api.mexc.com/api/v3/depth?symbol=" + symbol + "&limit=5000")
					.to("file:data/mexc/?fileName=${date:now:yyyyMMdd}/" + symbol + "/depth.json")
					.unmarshal().json(JsonLibrary.Jackson, OrderBook.class)
				.end()
			.end()
    		.bean(ComputeInitialPrice.BEAN_NAME)
    		.log("Computed buy limit order price: ${body}")
    		.removeHeader(EXCHANGE_INFO_HEADER_NAME)
    		.process().body(BigDecimal.class, p -> price = p);
    	
		// TODO kdyz price jeste neni nasetovana routou vyse - muze se stat, je to async
		from(String.format("quartz:placeOrder?cron=59+%d+%d+%d+%d+?+%d",
    			startMinute, startHour,
    			localDate.getDayOfMonth(), localDate.getMonthValue(), localDate.getYear()))
			// TODO reagovat na ruzne chyby, napr. too many requests, nepovoleni buy order s vyssi cenou nez napr. 0.5
    		.errorHandler(defaultErrorHandler()
    				.maximumRedeliveries(10)
    				.redeliveryDelay(1)
    				.logRetryAttempted(true)
    				.log(MyRouteBuilder.class)
    				.loggingLevel(LoggingLevel.INFO)
    				.retryAttemptedLogLevel(LoggingLevel.INFO))
    		.delay(950)
    		.setHeader("X-MEXC-APIKEY", constant(ACCESS_KEY))
    		.setHeader("Content-Type", constant("application/json"))
    		.setHeader(HttpConstants.HTTP_METHOD, constant(HttpMethods.POST))
    		.setHeader(HttpConstants.HTTP_QUERY).exchange(e -> {
    			long timestamp = new Date().getTime();
    			StringBuilder queryBuilder = new StringBuilder();
    			if (buyOrderPriceProperty.equalsIgnoreCase("market")) {
    				queryBuilder.append("type=MARKET").append("&quoteOrderQty=").append(usdtVolume);
    			} else {
    				queryBuilder.append("type=LIMIT")
    					.append("&quantity=").append(usdtVolume.divide(price, 2, RoundingMode.HALF_UP))
    					.append("&price=").append(price.toPlainString());
    			}
    			queryBuilder.append("&symbol=").append(symbol)
    				.append("&side=BUY")
    				.append("&timestamp=").append(timestamp);
    			String signature = Hex.encodeHexString(mac.doFinal(queryBuilder.toString().getBytes()));
    			return queryBuilder.append("&signature=").append(signature).toString();
    		})
    		.setBody().constant(null)
    		.log(LoggingLevel.DEBUG, "New order request: ${body}")
			.to("https://api.mexc.com/api/v3/order")
			.log(LoggingLevel.DEBUG, "New order response: ${body}")
			.unmarshal().json(JsonLibrary.Jackson, OrderResponse.class)
			.log("New order: ${body}")
			.process().body(OrderResponse.class, r -> buyOrderId = r.orderId());
		
		/*
		 * TODO
		 * - When application starts delete all existing listenKeys
		 * - Every 60 minutes sent keep alive request
		 * - After 24 hours reconnect - create a new listen key?
		 */
		from(DIRECT_SUBSCRIBE_WS_UPDATES)
			.setHeader("X-MEXC-APIKEY", constant(ACCESS_KEY))
			.setHeader("Content-Type", constant("application/json"))
			.setHeader(HttpConstants.HTTP_METHOD, constant(HttpMethods.POST))
			.setHeader(HttpConstants.HTTP_QUERY).exchange(e -> {
    			long timestamp = new Date().getTime();
    			String query = "timestamp=" + timestamp;
    			String signature = Hex.encodeHexString(mac.doFinal(query.getBytes()));
    			return query + "&signature=" + signature;
    		})
			.to("https://api.mexc.com/api/v3/userDataStream")
			.setBody().jsonpath("$.listenKey")
			.process(e -> {
				TemplatedRouteBuilder.builder(e.getContext(), USER_DATA_STREAM)
					.parameter("listenKey", e.getMessage().getBody(String.class))
					.add();
			})
			.setHeader("listenKey").body()
			.setBody(constant(
					String.format("{ \"method\":\"SUBSCRIPTION\", \"params\":[\"%s\", \"%s\", \"%s\", \"%s\"] }",
							SPOT_ACCOUNT_UPDATES_CHANNEL, SPOT_ORDER_UPDATES_CHANNEL,
							SPOT_DEPTH_UPDATES_CHANNEL_PREFIX + symbol, SPOT_TRADE_UPDATES_CHANNEL_PREFIX + symbol)))
			.toD("vertx-websocket:wss://wbs.mexc.com/ws?listenKey=${header.listenKey}&consumeAsClient=true");
		
		routeTemplate(USER_DATA_STREAM)
			.templateParameter("listenKey")
			.from("vertx-websocket:wss://wbs.mexc.com/ws?listenKey={{listenKey}}&consumeAsClient=true")
			.filter().jsonpath("$[?(@.c)]")
			.setHeader("channel").jsonpath("$.c")
			.choice()
				.when(header("channel").isEqualTo(SPOT_TRADE_UPDATES_CHANNEL_PREFIX + symbol))
					.to("file:data/mexc/?fileName=${date:now:yyyyMMdd}/" + symbol + "/trades.json&fileExist=Append&appendChars=\\n")
				.when(header("channel").isEqualTo(SPOT_DEPTH_UPDATES_CHANNEL_PREFIX + symbol))
					.to("file:data/mexc/?fileName=${date:now:yyyyMMdd}/" + symbol + "/orderBookUpdates.json&fileExist=Append&appendChars=\\n")
				.when(header("channel").isEqualTo(SPOT_ACCOUNT_UPDATES_CHANNEL))
					// TODO push sell orders
					.log("ACCOUNT: ${body}")
					// TODO aggregate to not push each partial fill? Is it necessary? There is usually huge pump
				.when(header("channel").isEqualTo(SPOT_ORDER_UPDATES_CHANNEL))
					.log("ORDERS: ${body}")
				.otherwise()
					.log(LoggingLevel.ERROR, "No WS processing route found ${header.channel}")
			;
    }

}
