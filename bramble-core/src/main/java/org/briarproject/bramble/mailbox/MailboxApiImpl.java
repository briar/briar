package org.briarproject.bramble.mailbox;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import org.briarproject.bramble.api.WeakSingletonProvider;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.IOException;

import javax.inject.Inject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.fasterxml.jackson.databind.MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES;
import static okhttp3.internal.Util.EMPTY_REQUEST;

@NotNullByDefault
class MailboxApiImpl implements MailboxApi {

	private final WeakSingletonProvider<OkHttpClient> httpClientProvider;
	private final JsonMapper mapper = JsonMapper.builder()
			.enable(BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)
			.build();

	@Inject
	MailboxApiImpl(WeakSingletonProvider<OkHttpClient> httpClientProvider) {
		this.httpClientProvider = httpClientProvider;
	}

	@Override
	public String setup(MailboxProperties properties)
			throws IOException, PermanentFailureException {
		if (!properties.isOwner) throw new IllegalArgumentException();
		Request request = getRequestBuilder(properties.token)
				.url(properties.baseUrl + "/setup")
				.put(EMPTY_REQUEST)
				.build();
		OkHttpClient client = httpClientProvider.get();
		Response response = client.newCall(request).execute();
		if (response.code() == 401) throw new PermanentFailureException();
		if (!response.isSuccessful()) throw new IOException();
		ResponseBody body = response.body();
		if (body == null) throw new PermanentFailureException();
		try {
			JsonNode node = mapper.readTree(body.string());
			JsonNode tokenNode = node.get("token");
			if (tokenNode == null) {
				throw new PermanentFailureException();
			}
			String ownerToken = tokenNode.textValue();
			if (ownerToken == null) {
				throw new PermanentFailureException();
			}
			return ownerToken;
		} catch (JacksonException e) {
			throw new PermanentFailureException();
		}
	}

	@Override
	public boolean checkStatus(MailboxProperties properties)
			throws IOException, PermanentFailureException {
		if (!properties.isOwner) throw new IllegalArgumentException();
		Request request = getRequestBuilder(properties.token)
				.url(properties.baseUrl + "/status")
				.build();
		OkHttpClient client = httpClientProvider.get();
		Response response = client.newCall(request).execute();
		if (response.code() == 401) throw new PermanentFailureException();
		return response.isSuccessful();
	}

	private Request.Builder getRequestBuilder(String token) {
		return new Request.Builder()
				.addHeader("Authorization", "Bearer " + token);
	}

}
