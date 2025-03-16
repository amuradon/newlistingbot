package cz.amuradon.tralon.newlisting.analyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cz.amuradon.tralon.newlisting.json.Trade;
import cz.amuradon.tralon.newlisting.json.TradeDetail;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@jakarta.ws.rs.Path("/charts")
public class ChartResource {

	private final Path dataRootDir;
	
	private final ObjectMapper mapper;
	
	@Inject
	public ChartResource(@ConfigProperty(name = "data.rootDir") final String dataRootDir) {
		this.dataRootDir = Paths.get(dataRootDir);
		mapper = new ObjectMapper();
	}
	
	@GET
	@Produces(MediaType.TEXT_HTML)
	public TemplateInstance index() {
		try {
			List<String> paths = Files.walk(dataRootDir).filter(Files::isRegularFile)
				.filter(p -> p.getFileName().toString().equalsIgnoreCase("trades.json"))
				.map(p -> dataRootDir.relativize(p).toString())
				.collect(Collectors.toList());
			return Templates.index(paths);
		} catch (IOException e) {
			throw new IllegalStateException("Could not read files", e);
		}
	}

	@GET
	@Produces(MediaType.TEXT_HTML)
	@jakarta.ws.rs.Path("/chart")
	public TemplateInstance chart(@RestQuery String filePath) {
		try {
			List<String> lines = Files.readAllLines(dataRootDir.resolve(Paths.get(filePath)));
			List<TradeDetail> tradeDetails = new ArrayList<>(lines.size());
			for (String line : lines) {
				Trade trade = mapper.readValue(line, Trade.class);
				for (TradeDetail tradeDetail : trade.data().deals()) {
					tradeDetails.add(tradeDetail);
				}
			}
			
			Collections.sort(tradeDetails, (o1, o2) -> (int) (o1.timestamp() - o2.timestamp()));
			long previousTimestamp = 0;
			long timestampAddition = 0;
			List<TimeSeries> timeSeries = new ArrayList<>(tradeDetails.size());
			for (TradeDetail trade : tradeDetails) {
				long timestamp = trade.timestamp();
//				if (previousTimestamp == timestamp) {
//					timestampAddition++;
//				} else {
//					previousTimestamp = timestamp;
//					timestampAddition = 0;
//				}
				timeSeries.add(new TimeSeries(timestamp + timestampAddition, trade.price().doubleValue()));
			}
			return Templates.chart(timeSeries);
		} catch (IOException e) {
			throw new IllegalStateException("Could not process chart file", e);
		}
	}

	@CheckedTemplate
	public static class Templates {
		public static native TemplateInstance index(List<String> fileNames);
		public static native TemplateInstance chart(List<TimeSeries> timeSeries);
	}
	
}
