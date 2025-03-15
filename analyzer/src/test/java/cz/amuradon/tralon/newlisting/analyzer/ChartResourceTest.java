package cz.amuradon.tralon.newlisting.analyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

public class ChartResourceTest {

	@Test
	public void test() throws IOException {
		ChartResource resource = new ChartResource("C:\\work\\workspace-java\\newlistingtrader\\trader\\data");
		resource.chart("mexc\\20250228\\CLAIUSDT\\trades.json");
	}
}
