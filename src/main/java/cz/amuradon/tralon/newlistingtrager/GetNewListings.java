package cz.amuradon.tralon.newlistingtrager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.Body;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;

import io.quarkus.logging.Log;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterForReflection
public class GetNewListings {

	private final ChromeDriver driver;
	
	private final SimpleDateFormat simpleDateFormat;
	
	private final Wait<WebDriver> wait;
	
	private final Set<String> reportedTokens;
	
	private final Set<String> blackListedTokens;
	
	public GetNewListings() {
		driver = new ChromeDriver();
		simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
		wait = new FluentWait<WebDriver>(driver).withTimeout(Duration.ofSeconds(10));
		reportedTokens = new HashSet<>();
		blackListedTokens = new HashSet<>();
		blackListedTokens.add("CATBOY");
	}
	
	/*
	 * - Ziskat data z CMC nebo CoinGecko
	 *   - Like, twitter, listing jinde, sentiment, BC atd.
	 *   - Link na blockchain explorer, abych zjistil, jaky BC to je, pokud neni v CMC nebo CoinGecko
	 *   	- Napr BLEND na Educhain tam neni
	 * - Sbirat trade a order book data v prvnich x minutach
	 */
	public void getNewListings(@Body ExchangeInfo exchangeInfo) {
		final Set<String> newTokens = exchangeInfo.symbols().stream()
				.filter(s -> s.status() == 2 && !s.isSpotTradingAllowed()).map(s -> s.baseAsset())
				.collect(Collectors.toSet());
		newTokens.removeAll(reportedTokens);
		newTokens.removeAll(blackListedTokens);
		
		List<NewListing> listings = new ArrayList<>();
		driver.get("https://www.mexc.com/newlisting");
		
		try {
			List<WebElement> elements = wait.until(
					ExpectedConditions.visibilityOfAllElementsLocatedBy(By.className("card_container___xZkC")));
			for (WebElement element: elements) {
				String baseToken = element.findElement(By.className("name")).getText();
				Date timestamp = simpleDateFormat.parse(element.findElement(By.className("time")).getText());
				boolean online = element.findElement(By.className("card_countDown__I7rTO"))
						.getDomAttribute("class").contains("online");
				listings.add(new NewListing(baseToken, timestamp, online));
			}
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		for (NewListing newListing : listings) {
			String baseToken = newListing.baseToken();
			if (!reportedTokens.contains(baseToken)
					&& !blackListedTokens.contains(baseToken)) {
				if (newTokens.remove(baseToken)) {
					Log.infof("Token %s in REST API and GUI: %s", baseToken, newListing);
				} else {
					Log.infof("Token %s in GUI only: %s", baseToken, newListing);
				}
				reportedTokens.add(baseToken);
				Log.infof("Adding token %s to reported: %s", baseToken, reportedTokens);
			}
		}
		
		for (String token : newTokens) {
			driver.get("https://www.mexc.com/exchange/" + token + "_USDT");
			try {
				wait.until(ExpectedConditions.visibilityOfElementLocated(
						By.className("trade-alert_tipsInfo__oHhYa")));
				blackListedTokens.add(token);
				Log.infof("Token %s halted, adding to blacklist: %s", token, blackListedTokens);
			} catch (TimeoutException e) {
				try {
					String timestamp = wait.until(ExpectedConditions.visibilityOfElementLocated(
						By.className("countDown_deadline__Inua0"))).getText();
					NewListing listing = new NewListing(token, simpleDateFormat.parse(timestamp), false);
					Log.infof("Token %s in REST API only: %s", token, listing);
					reportedTokens.add(token);
					Log.infof("Adding token %s to reported: %s", token, reportedTokens);
				} catch (ParseException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		}
	}
	
	
	private record NewListing(String baseToken, Date timestamp, boolean online) {
		
	}
}
