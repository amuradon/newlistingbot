package cz.amuradon.tralon.newlisting.trader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@ApplicationScoped
public class BeanConfig {
	
	public static final String SYMBOL = "symbol";
	
	public static final String DATA_DIR = "dataDir";

	@Inject
	public BeanConfig() {
		// TODO Auto-generated constructor stub
	}

	@Produces
	@ApplicationScoped
	@Named(DATA_DIR)
	public Path dataFilesDirPath(@Named(SYMBOL) String symbol) throws IOException {
		String dateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		Path path = Path.of("data", "mexc", dateFolder, symbol);
		Files.createDirectories(path);
		return path;
	}

	@Produces
	@Singleton
	@Named(SYMBOL)
	public String symbol(@ConfigProperty(name = "baseAsset") String baseAsset,
    		@ConfigProperty(name = "quoteAsset") String quoteAsset) {
		return baseAsset + quoteAsset;
	}
}
