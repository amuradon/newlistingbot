package cz.amuradon.tralon.newlisting.trader;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class MexcWsClientTest {
	
	private static final String JSON =
			"""
			{"c":"spot@private.orders.v3.api","d":{"i":"C02__523792577250553856035","c":"","o":1,"p":"0.01099",
			"v":"702.23","S":2,"a":"7.7175077","m":1,"A":"6.6175186","V":"602.14","lv":"100.09","s":3,
			"O":1740664117878,"ap":"0.01099","cv":"100.09","ca":"1.0999891"},"s":"VPTUSDT","t":1740664120600}
			""";

	@Test
	public void test() {
		MexcWsClient client = new MexcWsClient("null", "VPTUSDT", Path.of("test"));
		client.onMessage(JSON);
	}
}
