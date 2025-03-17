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
		
//		when(baseUpdatesMock.chats()).thenReturn(Collections.singletonList(channelMock));
//		when(baseUpdatesMock.updates()).thenReturn(Collections.singletonList(updateNewChannelMessageMock));
//		when(updateNewChannelMessageMock.message()).thenReturn(baseMessageMock);
//		when(channelMock.id()).thenReturn(wolfxChatId);
//		when(baseMessageMock.message()).thenReturn(WOLFX_MESSAGE);
		
		// TODO
		// Mock Binance server
		//   - Mock websockets
		// 	 - WS seems to connect to default port what is wrong
	}
	
	@AfterEach
	public void closeMocks() throws Exception {
		mocks.close();
	}
	
	@Test
	public void test() throws InterruptedException {
		traderAgent.prepare();
		Thread.sleep(60000);
	}

}
