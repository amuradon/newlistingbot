package cz.amuradon.tralon.newlisting.trader;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SymbolInfo(String symbol,
		String baseAsset, String quoteAsset,
		int status, boolean isSpotTradingAllowed,
		int baseAssetPrecision, int quotePrecision, int quoteAssetPrecision) {

}
