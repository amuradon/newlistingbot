package cz.amuradon.tralon.newlisting.trader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

public class UtilTest {

	@Test
	public void test() {
		Matcher matcher = Pattern.compile(".*(\\d+\\.\\d+)USDT").matcher("Order price cannot exceed 0.5USDT");
		matcher.find();
		System.out.println(matcher.group(1));
	}
}
