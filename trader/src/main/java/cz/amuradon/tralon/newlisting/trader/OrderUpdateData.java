package cz.amuradon.tralon.newlisting.trader;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderUpdateData(@JsonProperty("i") String orderId, @JsonProperty("d") OrderUpdateData data) {

}
