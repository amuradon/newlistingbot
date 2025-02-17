package cz.amuradon.tralon.newlistingtrager;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SymbolInfo(String baseAsset, String quoteAsset, int status, boolean isSpotTradingAllowed) {

}
