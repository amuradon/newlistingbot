package cz.amuradon.tralon.newlisting.trader.component;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.Map;
import java.util.Queue;

import org.mockito.Answers;
import org.mockito.MockMakers;

import io.fabric8.mockwebserver.ServerRequest;
import io.fabric8.mockwebserver.ServerResponse;
import io.fabric8.mockwebserver.internal.MockDispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

public class UrlParamsIgnoringDispatcher extends MockDispatcher {

	public UrlParamsIgnoringDispatcher(Map<ServerRequest, Queue<ServerResponse>> responses) {
		super(responses);
	}

	@Override
	public MockResponse dispatch(RecordedRequest request) {
		// It still cannot spy final class but it can mock it
		RecordedRequest requestSpy = mock(RecordedRequest.class, withSettings().mockMaker(MockMakers.INLINE)
				.spiedInstance(request).defaultAnswer(Answers.CALLS_REAL_METHODS));
		when(requestSpy.getPath()).thenAnswer(i -> {
			String path = request.getPath();
			System.out.println("DISPATCHER: " + path + " req " + request.hashCode());
			return  path.contains("?") ? path.substring(0, path.indexOf("?")) : path;
		});
		return super.dispatch(requestSpy);
	}
}
