package cz.amuradon.tralon.newlisting.trader;

import java.math.BigDecimal;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

import cz.amuradon.tralon.newlisting.trader.ComputeInitialPrice;
import cz.amuradon.tralon.newlisting.trader.ExchangeInfo;
import cz.amuradon.tralon.newlisting.trader.OrderBook;
import cz.amuradon.tralon.newlisting.trader.SymbolInfo;

public class ComputeInitialPriceTest {

	@Test
	public void testSlippage() {
		ComputeInitialPrice compute = new ComputeInitialPrice("slippage:20.0");
		List<List<BigDecimal>> asks = new ArrayList<>();
		asks.add(Lists.newArrayList(new BigDecimal("0.3"), new BigDecimal("5")));
		asks.add(Lists.newArrayList(new BigDecimal("0.35"), new BigDecimal("20")));
		asks.add(Lists.newArrayList(new BigDecimal("0.36"), new BigDecimal("30")));
		
		OrderBook orderBook = new OrderBook(null, asks); 
		BigDecimal result = compute.execute("VPTUSDT",
				new ExchangeInfo(Collections.singletonList(
						new SymbolInfo("VPTUSDT", "VPT", "USDT", 1, true, 2, 6, 6))),
				orderBook);
		Assertions.assertEquals(new BigDecimal("0.408000"), result);
	}
	
	@Test
	public void testFixed() {
		ComputeInitialPrice compute = new ComputeInitialPrice("manual:0.00125");
		BigDecimal result = compute.execute("VPTUSDT", null, null);
		Assertions.assertEquals(new BigDecimal("0.00125"), result);
	}
	
}
