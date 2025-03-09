package cz.amuradon.tralon.newlisting.trader;

import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.Map.Entry;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

public class TraderAgentTest {

	private static final String HMAC_SHA256 = "HmacSHA256";

	@Test
	public void test() throws Exception {
		Mac mac = Mac.getInstance(HMAC_SHA256);
		mac.init(new SecretKeySpec("51c13954c8e043cd9215b9f32c8eaf86".getBytes(), HMAC_SHA256));
		
		long timestamp = new Date().getTime();
		Map<String, String> queryParams = new LinkedHashMap<>();
		queryParams.put("timestamp", String.valueOf(timestamp));
		queryParams.put("recvWindow", String.valueOf(20000));
		
		StringJoiner joiner = new StringJoiner("&");
    	for (Entry<String, String> entry : queryParams.entrySet()) {
			joiner.add(entry.getKey() + "=" + entry.getValue());
		}
    	String signature = HexFormat.of().formatHex(mac.doFinal(joiner.toString().getBytes()));
    	queryParams.put("signature", signature);
    	joiner.add("signature=" + signature);
    	System.out.println("https://api.mexc.com/api/v3/userDataStream?" + joiner.toString());
	}
}
