package cz.amuradon.tralon.newlisting.trader;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderUpdateData(
		@JsonProperty("i") String orderId,
//		@JsonProperty("o") int orderType, // TODO enum?
		@JsonProperty("S") Side side,
//		@JsonProperty("p") BigDecimal price,
		@JsonProperty("ap") BigDecimal averagePrice,
		@JsonProperty("v") BigDecimal quantityBase,
//		@JsonProperty("a") BigDecimal amountQuote,
//		@JsonProperty("V") BigDecimal remainingQuantityBase,
//		@JsonProperty("A") BigDecimal remainingAmountQuote,
//		@JsonProperty("d") boolean maker,
		@JsonProperty("s") Status status
		) {

}
