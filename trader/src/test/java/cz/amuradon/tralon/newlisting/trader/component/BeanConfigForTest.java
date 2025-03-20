package cz.amuradon.tralon.newlisting.trader.component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import cz.amuradon.tralon.newlisting.trader.BeanConfig;
import io.quarkus.test.Mock;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

@Mock
@ApplicationScoped
public class BeanConfigForTest {

	@Alternative
	@Priority(1)
	@Produces
	@ApplicationScoped
	@Named(BeanConfig.DATA_DIR)
	public Path dataFilesDirPath(@Named(BeanConfig.SYMBOL) String symbol) throws IOException {
		Path path = Path.of("test-data", symbol);
		Files.createDirectories(path);
		return path;
	}
}
