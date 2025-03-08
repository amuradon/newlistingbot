package cz.amuradon.tralon.newlistingtrager;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;

public class HttpRequestsAggregationStrategy implements AggregationStrategy {

	@Override
	public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
		return aggregate(oldExchange, newExchange, new DefaultExchange(newExchange.getContext()));
	}

	@Override
	public Exchange aggregate(Exchange oldExchange, Exchange newExchange, Exchange inputExchange) {
		Object body = newExchange.getMessage().getBody();
		if (body instanceof ExchangeInfo) {
			inputExchange.getMessage().setHeader(MyRouteBuilder.EXCHANGE_INFO_HEADER_NAME, body);
		} else if (body instanceof OrderBook) {
			inputExchange.getMessage().setBody(body);
		}
		
		return inputExchange;
	}
}
