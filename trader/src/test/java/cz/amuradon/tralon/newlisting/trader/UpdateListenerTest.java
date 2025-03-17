package cz.amuradon.tralon.newlisting.trader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HexFormat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockMakers;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import cz.amuradon.tralon.newlisting.trader.RequestBuilder.NewOrderRequestBuilder;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UpdateListenerTest {
	
	private static final String ORDER_UPDATE_JSON =
			"""
			{"c":"spot@private.orders.v3.api","d":{"i":"C02__523792577250553856035","c":"","o":1,"p":"0.01099",
			"v":"702.23","S":2,"a":"7.7175077","m":1,"A":"6.6175186","V":"602.14","lv":"100.09","s":3,
			"O":1740664117878,"ap":"0.01099","cv":"100.09","ca":"1.0999891"},"s":"VPTUSDT","t":1740664120600}
			""";
	
	private static final String TRADE_JSON =
			"""
			{"c":"spot@public.deals.v3.api@VPTUSDT","d":{"deals":[{"p":"0.01099","v":"48704.00","S":1,
			"t":1741006800039}],"e":"spot@public.deals.v3.api"},"s":"VPTUSDT","t":1741006800040}
			""";
	
	@Mock
	private RequestBuilder requestBuilderMock;
	
	@Mock
	private NewOrderRequestBuilder newOrderRequestBuilderMock;
	
	private UpdatesListener listener;
	
	@BeforeEach
	public void prepare() {
		when(requestBuilderMock.newOrder()).thenReturn(newOrderRequestBuilderMock);
		
		DataHolder dataHolder = new DataHolder();
		listener = new UpdatesListener(15, 50, 50, "VPTUSDT", Paths.get("test"), dataHolder, requestBuilderMock);
	}
//
//	@Test
//	public void testOrderUpdate() {
//		MexcWsClient client = new MexcWsClient("null", 4, "VPTUSDT", Path.of("test"),
//				new BuyOrderIdHolder(), new PriceScaleHolder());
//		client.onMessage(ORDER_UPDATE_JSON);
//	}
//

	@Test
	public void testTrade() {
		
//		try (MockedStatic<Files> mb = mockStatic(Files.class, withSettings()
//	             .mockMaker(MockMakers.INLINE))) {
//			mb.when(() -> Files.writeString(any(Path.class), any(), any())).thenReturn(Path.of("test"));
//			MexcWsClient client = new MexcWsClient("null", 4, "VPTUSDT", Path.of("test"),
//					new BuyOrderIdHolder(), new PriceScaleHolder());
//			client.onMessage(TRADE_JSON);
//		}
	}
}
