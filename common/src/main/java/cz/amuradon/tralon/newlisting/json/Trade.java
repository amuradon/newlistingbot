package cz.amuradon.tralon.newlisting.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Trade(@JsonProperty("d") TradeData data) {

}
