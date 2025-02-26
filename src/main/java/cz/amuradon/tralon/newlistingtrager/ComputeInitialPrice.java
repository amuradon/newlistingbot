package cz.amuradon.tralon.newlistingtrager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.apache.camel.Body;
import org.apache.camel.Handler;
import org.apache.camel.Header;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@ApplicationScoped
@Named(ComputeInitialPrice.BEAN_NAME)
@RegisterForReflection
public class ComputeInitialPrice {

	public static final String BEAN_NAME = "computeInitialPrice";
	
	private final String slippage;
	
	@Inject
	public ComputeInitialPrice(@ConfigProperty(name = "slippage") final String slippage) {
		this.slippage = slippage;
	}
	
	@Handler
	public BigDecimal execute(@Header(MyRouteBuilder.SYMBOL_HEADER_NAME) String symbol,
			@Header(MyRouteBuilder.EXCHANGE_INFO_HEADER_NAME) ExchangeInfo exchangeInfo,
			@Body OrderBook orderBook) {
		
		int priceScale = 4;
		for (SymbolInfo symbolInfo: exchangeInfo.symbols()) {
			if (symbolInfo.symbol().equalsIgnoreCase(symbol)) {
				priceScale = symbolInfo.quotePrecision();
				break;
			}
		}

		List<List<BigDecimal>> asks = orderBook.asks();
		BigDecimal priceSum = BigDecimal.ZERO;
		BigDecimal volumeSum = BigDecimal.ZERO;

		if (!asks.isEmpty()) {
			BigDecimal prevPrice = null;
			
			for (List<BigDecimal> ask: asks) {
				BigDecimal price = ask.get(0);
				BigDecimal volume = ask.get(1);
				
				if (prevPrice != null && getPercentDiff(prevPrice, price).compareTo(new BigDecimal(10)) < 0) {
					break;
				} else {
					priceSum = priceSum.add(price.multiply(volume));
					volumeSum = volumeSum.add(volume);
					prevPrice = price;
				}
			}
			
			BigDecimal slip = new BigDecimal(slippage)
					.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP).add(BigDecimal.ONE);
			return priceSum.divide(volumeSum, 10, RoundingMode.HALF_UP)
					.multiply(slip).setScale(priceScale, RoundingMode.HALF_UP);
		} else {
			return BigDecimal.ZERO;
		}

	}

	private BigDecimal getPercentDiff(BigDecimal number, BigDecimal nextNumber) {
		return nextNumber.subtract(number).divide(number, 10, RoundingMode.HALF_UP).multiply(new BigDecimal(100));
	}
}
