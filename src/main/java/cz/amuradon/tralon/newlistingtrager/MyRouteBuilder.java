package cz.amuradon.tralon.newlistingtrager;

import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

public class MyRouteBuilder extends EndpointRouteBuilder {

    private static final String PLACE_ORDER = "placeOrder";

	@Override
    public void configure() throws Exception {
//        from("timer:getDataMexcRest?period=10000&fixedRate=true")
//            .to("https://api.mexc.com/api/v3/exchangeInfo")
//            .unmarshal().json(JsonLibrary.Jackson, ExchangeInfo.class)
////            .split(bodyAs(ExchangeInfo.class).method("symbols"))
////            .filter().body(SymbolInfo.class, s -> s.status() == 2 && !s.isSpotTradingAllowed())
//            .bean(GetNewListings.class, "getNewListings");
    	
    	//from("quartz:startListing?cron=0+10+10+15+2+?+2025")
    	from("timer:getDataMexcRest?repeatCount=1")
    		.to("https://api.mexc.com/api/v3/depth?symbol=AICEUSDT&limit=500")
    		.unmarshal().json(JsonLibrary.Jackson, OrderBook.class)
    		.log("${body}");
    	
//    	from("timer:placeOrder")
//    		.routeId(PLACE_ORDER);
    }

}
