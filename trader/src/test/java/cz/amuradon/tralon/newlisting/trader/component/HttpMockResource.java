package cz.amuradon.tralon.newlisting.trader.component;

import java.nio.file.Files;
import java.nio.file.Path;
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

	private final Path dataRootPath =
			Path.of("C:\\work\\workspace-java\\newlistingbot\\trader\\data\\mexc\\\\20250319\\\\RXUSDT");
	
	private final String symbol = "RXUSDT";
	
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
			.expect(String.format("{ \"method\":\"SUBSCRIPTION\", \"params\":[\"spot@private.account.v3.api\","
					+ " \"spot@private.orders.v3.api\", \"spot@public.increase.depth.v3.api@%1$s\", \"spot@public.deals.v3.api@%1$s\"] }", symbol))
			.andEmit("{ \"id\":1}").once()
				.immediately().andEmit(
						String.format(
						"""
						{"c":"spot@public.deals.v3.api@RXUSDT","d":{"deals":[{"p":"0.00500","v":"2000.00","S":1,"t":1742385600023}],"e":"spot@public.deals.v3.api"},"s":"RXUSDT","t":1742385600025}
						""", symbol, new Date().getTime()))
			// TODO on new order publish WS order updates - partial and/or full fill
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
			"symbol": "%s",
			"orderId": "06a480e69e604477bfb48dddd5f0b750",
			"orderListId": -1,
			"price": "0.1",
			"origQty": "50",
			"type": "LIMIT",
			"side": "BUY",
			"transactTime": %d
			}	
			""", symbol, new Date().getTime()));
	}

	private void mockCancelOrder() {
		expectDelete("/order", () ->
		String.format(
			"""
			{
			  "symbol": "RXBTC",
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
		expectGet("/depth", Path.of("depth.json"));
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
		expectGet("/exchangeInfo", Path.of("exchangeInfo.json"));
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
	
	private void expectGet(String path, SupplierHandled<Object> body) {
		server.expect().withPath("/api/v3" + path)
			.andReply(new MyResponseProvider(body)).always();
	}

	private void expectGet(String path, Path filePath) {
		expectGet(path, () -> Files.readString(dataRootPath.resolve(filePath)));
	}

	private void expectPost(String path, SupplierHandled<Object> body) {
		server.expect().post().withPath("/api/v3" + path)
			.andReply(new MyResponseProvider(body)).always();
	}

	private void expectDelete(String path, SupplierHandled<Object> body) {
		server.expect().delete().withPath("/api/v3" + path)
			.andReply(new MyResponseProvider(body)).always();
	}
	
	private static class MyResponseProvider implements ResponseProvider<Object> {

		private final SupplierHandled<Object> bodySupplier;
		
		private Headers headers = new Headers.Builder().add("Content-Type", "application/json").build();
		
		public MyResponseProvider(SupplierHandled<Object> bodySupplier) {
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
	
	private interface SupplierHandled<T> extends Supplier<T> {

		
		default T get() {
			try {
				return getHandled();
			} catch (Exception e) {
				throw new RuntimeException("", e);
			}
		}
		
		T getHandled() throws Exception;
	}
}
