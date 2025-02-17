package cz.amuradon.tralon.newlistingtrager;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderBook(@JsonProperty("bids") List<List<String>> bids, @JsonProperty("asks") List<List<String>> asks) {

}
