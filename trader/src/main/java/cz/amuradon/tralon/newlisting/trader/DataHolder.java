package cz.amuradon.tralon.newlisting.trader;

import java.math.BigDecimal;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DataHolder {

	private String buyOrderId;
	
	private String buyClientOrderId;
	
	private int priceScale;
	
	private BigDecimal initialBuyPrice;
	
	public String getBuyOrderId() {
		return buyOrderId;
	}

	public void setBuyOrderId(String buyOrderId) {
		this.buyOrderId = buyOrderId;
	}

	public int getPriceScale() {
		return priceScale;
	}

	public void setPriceScale(int priceScale) {
		this.priceScale = priceScale;
	}

	public BigDecimal getInitialBuyPrice() {
		return initialBuyPrice;
	}

	public void setInitialBuyPrice(BigDecimal initialBuyPrice) {
		this.initialBuyPrice = initialBuyPrice;
	}

	public String getBuyClientOrderId() {
		return buyClientOrderId;
	}

	public void setBuyClientOrderId(String buyClientOrderId) {
		this.buyClientOrderId = buyClientOrderId;
	}


}
