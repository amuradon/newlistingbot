package cz.amuradon.tralon.newlisting.trader.component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import cz.amuradon.tralon.newlisting.trader.TraderAgent;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(HttpMockResource.class)
public class ComponentTest {

	@Inject
	TraderAgent traderAgent;
	
	private AutoCloseable mocks;
	
	@BeforeEach
	public void prepare() {
		// In QuarkusTest the annotation @ExtendWith(MockitoExtension.class) does not work, so doing manual way
		mocks = MockitoAnnotations.openMocks(this);
	}
	
	@AfterEach
	public void closeMocks() throws Exception {
		mocks.close();
	}
	
	@Test
	public void test() throws InterruptedException {
		traderAgent.prepare();
		traderAgent.placeNewBuyOrder();
		
		// XXX
		Thread.sleep(10000);
	}

}
