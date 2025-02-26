package cz.amuradon.tralon.newlistingtrager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Date;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.component.http.HttpConstants;
import org.apache.camel.component.http.HttpMethods;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.commons.codec.binary.Hex;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MyRouteBuilder extends EndpointRouteBuilder {
	
	public static final String SYMBOL_HEADER_NAME = "Symbol";
	
	public static final String EXCHANGE_INFO_HEADER_NAME = "ExchangeInfo";
	
	private static final String DIRECT_PLACE_NEW_ORDER = "direct:placeNewOrder";

	private static final String DIRECT_PREPARE_BUY_ORDER_DATA = "direct:prepareBuyOrderData";

	private static final String DIRECT_SUBSCRIBE_ORDER_BOOK_UPDATES = "direct:subscribeOrderBookUpdates";

	private static final String DIRECT_SUBSCRIBE_TRADES = "direct:subscribeTrades";
	
	private static final String HMAC_SHA256 = "HmacSHA256";
	private static final String ACCESS_KEY = "mx0vgl5eTwQI22AEqq";
	private static final String SECRET_KEY = "51c13954c8e043cd9215b9f32c8eaf86";

    private final BigDecimal usdtVolume;
    private final String symbol;
    
	private Mac mac;
    
    @Inject
    public MyRouteBuilder(@ConfigProperty(name = "usdtVolume") final String usdtVolume,
    		@ConfigProperty(name = "symbol") final String symbol) {
    	this.usdtVolume = new BigDecimal(usdtVolume);
    	this.symbol = symbol;
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
		// Scanning of new listings
//        from("timer:getDataMexcRest?period=10000&fixedRate=true")
//            .to("https://api.mexc.com/api/v3/exchangeInfo")
//            .unmarshal().json(JsonLibrary.Jackson, ExchangeInfo.class)
////            .split(bodyAs(ExchangeInfo.class).method("symbols"))
////            .filter().body(SymbolInfo.class, s -> s.status() == 2 && !s.isSpotTradingAllowed())
//            .bean(GetNewListings.class, "getNewListings");
    	
		
		LocalDate localDate = LocalDate.now();
//    	from(String.format("quartz:startListing?cron=57+09+16+%d+%d+?+%d",
//    			localDate.getDayOfMonth(), localDate.getMonthValue(), localDate.getYear()))
		from("timer:placeOrder?repeatCount=1")
			.multicast()
				.to(DIRECT_SUBSCRIBE_TRADES)
				.to(DIRECT_SUBSCRIBE_ORDER_BOOK_UPDATES)
				.to(DIRECT_PREPARE_BUY_ORDER_DATA);
		
		from(DIRECT_PREPARE_BUY_ORDER_DATA)
			.setHeader(SYMBOL_HEADER_NAME).constant(symbol)
			.multicast(new HttpRequestsAggregationStrategy(), true)
				.pipeline()
					.to("https://api.mexc.com/api/v3/exchangeInfo?symbol=" + symbol)
					.unmarshal().json(JsonLibrary.Jackson, ExchangeInfo.class)
				.end()
				.pipeline()
					.to("https://api.mexc.com/api/v3/depth?symbol=" + symbol + "&limit=5000")
					.unmarshal().json(JsonLibrary.Jackson, OrderBook.class)
				.end()
			.end()
    		.bean(ComputeInitialPrice.BEAN_NAME)
    		.removeHeader(EXCHANGE_INFO_HEADER_NAME)
    		.to(DIRECT_PLACE_NEW_ORDER);
    	
    	from(DIRECT_PLACE_NEW_ORDER)
    		.errorHandler(defaultErrorHandler().maximumRedeliveries(50).redeliveryDelay(200))
    		.setHeader("X-MEXC-APIKEY", constant(ACCESS_KEY))
    		.setHeader("Content-Type", constant("application/json"))
    		.setHeader(HttpConstants.HTTP_METHOD, constant(HttpMethods.POST))
    		.setHeader(HttpConstants.HTTP_QUERY).exchange(e -> {
    			BigDecimal price = e.getMessage().getBody(BigDecimal.class);
    			long timestamp = new Date().getTime();
    			String query = "symbol=" + symbol + "&side=BUY&type=LIMIT&quantity="
    					+ usdtVolume.divide(price, 2, RoundingMode.HALF_UP) + "&price=" + price.toPlainString()
    					+ "&timestamp=" + timestamp;
    			String signature = Hex.encodeHexString(mac.doFinal(query.getBytes()));
    			return query + "&signature=" + signature;
    		})
    		.setBody().constant(null)
			.to("https://api.mexc.com/api/v3/order")
			.log("${body}");
		
		from("vertx-websocket:wss://wbs.mexc.com/ws?consumeAsClient=true")
			.choice()
				.when().jsonpath("$[?(@.c == 'spot@public.deals.v3.api@" + symbol + "')]")
					.to("file:data/mexc/?fileName=${date:now:yyyyMMdd}/" + symbol + "/trades.json&fileExist=Append&appendChars=\\n")
				.when().jsonpath("$[?(@.c == 'spot@public.increase.depth.v3.api@" + symbol + "')]")
					.to("file:data/mexc/?fileName=${date:now:yyyyMMdd}/" + symbol + "/orderBookUpdates.json&fileExist=Append&appendChars=\\n")
				.otherwise()
					.log(LoggingLevel.ERROR, "No choice found");
		
		from(DIRECT_SUBSCRIBE_TRADES)
			.setBody(constant("{ \"method\":\"SUBSCRIPTION\", \"params\":[\"spot@public.deals.v3.api@" + symbol + "\"] }"))
			.to("vertx-websocket:wss://wbs.mexc.com/ws?consumeAsClient=true");
		
		from(DIRECT_SUBSCRIBE_ORDER_BOOK_UPDATES)
			.setBody(constant("{ \"method\":\"SUBSCRIPTION\", \"params\":[\"spot@public.increase.depth.v3.api@" + symbol + "\"] }"))
			.to("vertx-websocket:wss://wbs.mexc.com/ws?consumeAsClient=true");
		
    }

}
