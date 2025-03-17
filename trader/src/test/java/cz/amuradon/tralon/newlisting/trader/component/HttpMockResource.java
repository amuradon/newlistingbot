package cz.amuradon.tralon.newlisting.trader.component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.function.Supplier;

import io.fabric8.mockwebserver.DefaultMockServer;
import io.fabric8.mockwebserver.MockServer;
import io.fabric8.mockwebserver.ServerRequest;
import io.fabric8.mockwebserver.ServerResponse;
import io.fabric8.mockwebserver.utils.ResponseProvider;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import jakarta.inject.Inject;
import okhttp3.Headers;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class HttpMockResource implements QuarkusTestResourceLifecycleManager {

	private DefaultMockServer server;

	@Override
	public Map<String, String> start() {
		final String listenKey = "pqia91ma19a5s61cv6a81va65sdf19v8a65a1a5s61cv6a81va65sdf19v8a65a1";
		HashMap<ServerRequest, Queue<ServerResponse>> responses = new HashMap<>();
		server = new DefaultMockServer(new io.fabric8.mockwebserver.Context(), new MockWebServer(), responses,
				new UrlParamsIgnoringDispatcher(responses), false);
		
		mockUserDataStream(listenKey);
		mockExchangeInfo();
		mockDepth();
		mockNewOrder();
		mockCancelOrder();
		
		server.expect().get().withPath("/ws").andUpgradeToWebSocket()
			.open()
			.expect("{ \"method\":\"SUBSCRIPTION\", \"params\":[\"spot@private.account.v3.api\", \"spot@private.orders.v3.api\", \"spot@public.increase.depth.v3.api@XPUSDT\", \"spot@public.deals.v3.api@XPUSDT\"] }")
//			.expectHttpRequest("/ws")
			.andEmit("{ \"id\":1}").once()
				.waitFor(1000).andEmit(
						String.format(
						"""
						{
						"channel": "spot@public.deals.v3.api@VPTUSDT",
						"publicdeals": {
						"dealsList": [
						{
						"price": "93220.00",
						"quantity": "0.04438243",
						"tradetype": 2,
						"time": %1$d
						}
						],
						"eventtype": "spot@public.deals.v3.api@100ms" 
						},
						"symbol": "VPTUSDT",
						"sendtime": %1$d
						}
						""", new Date().getTime()))
			.done().once();
		
		server.start();
		
		return Map.of(
				"mexc-api.websocket.url", "ws://localhost:" + server.getPort() + "/ws",
				"quarkus.rest-client.mexc-api.url", "http://localhost:" + server.getPort() + "/api/v3"
				);
	}

	private void mockNewOrder() {
		expectPost("/order", () ->
		String.format(
			"""
			{
			"symbol": "VPTUSDT",
			"orderId": "06a480e69e604477bfb48dddd5f0b750",
			"orderListId": -1,
			"price": "0.1",
			"origQty": "50",
			"type": "LIMIT",
			"side": "BUY",
			"transactTime": %d
			}	
			""", new Date().getTime()));
	}

	private void mockCancelOrder() {
		expectDelete("/order", () ->
		String.format(
			"""
			{
			  "symbol": "LTCBTC",
			  "origClientOrderId": "myOrder1",
			  "orderId": 4,
			  "clientOrderId": "cancelMyOrder1",
			  "price": "2.00000000",
			  "origQty": "1.00000000",
			  "executedQty": "0.00000000",
			  "cummulativeQuoteQty": "0.00000000",
			  "status": "CANCELED",
			  "timeInForce": "GTC",
			  "type": "LIMIT",
			  "side": "BUY"
			}
			""", new Date().getTime()));
	}

	private void mockDepth() {
		expectGet("/depth", () ->
			String.format(
			"""
			{
			"lastUpdateId":%d,
			"bids":[["0.007157","10.00"],["0.007141","20.00"]],
			"asks":[["0.007165","10.00"],["0.007168","20.00"]]
			}
			""", new Date().getTime()));
	}

	private void mockUserDataStream(final String listenKey) {
		expectPost("/userDataStream", () ->
			String.format("""
			{
				"listenKey": "%s"
			}
			""", listenKey));
	}

	private void mockExchangeInfo() {
		expectGet("/exchangeInfo", () ->
			String.format("""
			{
			"timezone":"CST",
			"serverTime":%d,
			"rateLimits":[],
			"exchangeFilters":[],
			"symbols":
			  [
			    {
			      "symbol":"BROCKUSDT",
			      "status":"1",
			      "baseAsset":"BROCK",
			      "baseAssetPrecision":2,
			      "quoteAsset":"USDT",
			      "quotePrecision":5,
			      "quoteAssetPrecision":5,
			      "baseCommissionPrecision":2,
			      "quoteCommissionPrecision":5,
			      "orderTypes":["LIMIT","MARKET","LIMIT_MAKER"],
			      "isSpotTradingAllowed":true,
			      "isMarginTradingAllowed":false,
			      "quoteAmountPrecision":"1",
			      "baseSizePrecision":"0",
			      "permissions":["SPOT"],
			      "filters":[],
			      "maxQuoteAmount":"2000000",
			      "makerCommission":"0",
			      "takerCommission":"0.0005",
			      "quoteAmountPrecisionMarket":"1",
			      "maxQuoteAmountMarket":"100000",
			      "fullName":"Bitrock",
			      "tradeSideType":1,
			      "st":false
			    },
			    {
			      "symbol":"VPTUSDT",
			      "status":"1",
			      "baseAsset":"VPT",
			      "baseAssetPrecision":2,
			      "quoteAsset":"USDT",
			      "quotePrecision":6,
			      "quoteAssetPrecision":6,
			      "baseCommissionPrecision":2,
			      "quoteCommissionPrecision":6,
			      "orderTypes":["LIMIT","MARKET","LIMIT_MAKER"],
			      "isSpotTradingAllowed":true,
			      "isMarginTradingAllowed":false,
			      "quoteAmountPrecision":"1",
			      "baseSizePrecision":"0",
			      "permissions":["SPOT"],
			      "filters":[],
			      "maxQuoteAmount":"2000000",
			      "makerCommission":"0",
			      "takerCommission":"0.0005",
			      "quoteAmountPrecisionMarket":"1",
			      "maxQuoteAmountMarket":"100000",
			      "fullName":"Veritas",
			      "tradeSideType":1,
			      "st":false
			    }
			  ]
			}			      	
			""", new Date().getTime()));
	}

	@Override
	public void stop() {
		if (server != null) {
			server.shutdown();
		}
	}

	@Override
	public void inject(TestInjector testInjector) {
		testInjector.injectIntoFields(server,
				new TestInjector.AnnotatedAndMatchesType(Inject.class, MockServer.class));
	}
	
	private void expectGet(String path, Supplier<Object> body) {
		server.expect().withPath("/api/v3" + path)
			.andReply(new MyResponseProvider(body)).always();
	}

	private void expectPost(String path, Supplier<Object> body) {
		server.expect().post().withPath("/api/v3" + path)
			.andReply(new MyResponseProvider(body)).always();
	}

	private void expectDelete(String path, Supplier<Object> body) {
		server.expect().delete().withPath("/api/v3" + path)
			.andReply(new MyResponseProvider(body)).always();
	}
	
	private static class MyResponseProvider implements ResponseProvider<Object> {

		private final Supplier<Object> bodySupplier;
		
		private Headers headers = new Headers.Builder().add("Content-Type", "application/json").build();
		
		public MyResponseProvider(Supplier<Object> bodySupplier) {
			this.bodySupplier = bodySupplier;
		}

		@Override
		public Object getBody(RecordedRequest request) {
			return bodySupplier.get();
		}

		@Override
		public int getStatusCode(RecordedRequest request) {
			return 200;
		}

		@Override
		public Headers getHeaders() {
			return headers;
		}

		@Override
		public void setHeaders(Headers headers) {
			this.headers = headers;
		}
		
	}
}
