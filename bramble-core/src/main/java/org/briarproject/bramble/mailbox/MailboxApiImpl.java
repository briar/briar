package org.briarproject.bramble.mailbox;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import org.briarproject.bramble.api.WeakSingletonProvider;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.fasterxml.jackson.databind.MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES;
import static java.util.Objects.requireNonNull;
import static okhttp3.internal.Util.EMPTY_REQUEST;
import static org.briarproject.bramble.util.StringUtils.fromHexString;

@NotNullByDefault
class MailboxApiImpl implements MailboxApi {

	private final WeakSingletonProvider<OkHttpClient> httpClientProvider;
	private final JsonMapper mapper = JsonMapper.builder()
			.enable(BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)
			.build();
	private static final MediaType JSON =
			requireNonNull(MediaType.parse("application/json; charset=utf-8"));

	@Inject
	MailboxApiImpl(WeakSingletonProvider<OkHttpClient> httpClientProvider) {
		this.httpClientProvider = httpClientProvider;
	}

	@Override
	public String setup(MailboxProperties properties)
			throws IOException, ApiException {
		if (!properties.isOwner()) throw new IllegalArgumentException();
		Request request = getRequestBuilder(properties.getAuthToken())
				.url(properties.getOnionAddress() + "/setup")
				.put(EMPTY_REQUEST)
				.build();
		OkHttpClient client = httpClientProvider.get();
		Response response = client.newCall(request).execute();
		// TODO consider throwing a special exception for the 401 case
		if (response.code() == 401) throw new ApiException();
		if (!response.isSuccessful()) throw new ApiException();
		ResponseBody body = response.body();
		if (body == null) throw new ApiException();
		try {
			JsonNode node = mapper.readTree(body.string());
			JsonNode tokenNode = node.get("token");
			if (tokenNode == null) {
				throw new ApiException();
			}
			String ownerToken = tokenNode.textValue();
			if (ownerToken == null || !isValidToken(ownerToken)) {
				throw new ApiException();
			}
			return ownerToken;
		} catch (JacksonException e) {
			throw new ApiException();
		}
	}

	private boolean isValidToken(String token) {
		if (token.length() != 64) return false;
		try {
			// try to convert to bytes
			fromHexString(token);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	@Override
	public boolean checkStatus(MailboxProperties properties)
			throws IOException, ApiException {
		if (!properties.isOwner()) throw new IllegalArgumentException();
		Request request = getRequestBuilder(properties.getAuthToken())
				.url(properties.getOnionAddress() + "/status")
				.build();
		OkHttpClient client = httpClientProvider.get();
		Response response = client.newCall(request).execute();
		if (response.code() == 401) throw new ApiException();
		return response.isSuccessful();
	}

	@Override
	public void addContact(MailboxProperties properties, MailboxContact contact)
			throws IOException, ApiException,
			TolerableFailureException {
		if (!properties.isOwner()) throw new IllegalArgumentException();
		byte[] bodyBytes = mapper.writeValueAsBytes(contact);
		RequestBody body = RequestBody.create(JSON, bodyBytes);
		Request request = getRequestBuilder(properties.getAuthToken())
				.url(properties.getOnionAddress() + "/contacts")
				.post(body)
				.build();
		OkHttpClient client = httpClientProvider.get();
		Response response = client.newCall(request).execute();
		if (response.code() == 409) throw new TolerableFailureException();
		if (!response.isSuccessful()) throw new ApiException();
	}

	@Override
	public void deleteContact(MailboxProperties properties, ContactId contactId)
			throws IOException, ApiException {
		if (!properties.isOwner()) throw new IllegalArgumentException();
		String url = properties.getOnionAddress() + "/contacts/" +
				contactId.getInt();
		Request request = getRequestBuilder(properties.getAuthToken())
				.delete()
				.url(url)
				.build();
		OkHttpClient client = httpClientProvider.get();
		Response response = client.newCall(request).execute();
		if (response.code() != 200) throw new ApiException();
	}

	@Override
	public Collection<ContactId> getContacts(MailboxProperties properties)
			throws IOException, ApiException {
		if (!properties.isOwner()) throw new IllegalArgumentException();
		Request request = getRequestBuilder(properties.getAuthToken())
				.url(properties.getOnionAddress() + "/contacts")
				.build();
		OkHttpClient client = httpClientProvider.get();
		Response response = client.newCall(request).execute();
		if (response.code() != 200) throw new ApiException();

		ResponseBody body = response.body();
		if (body == null) throw new ApiException();
		try {
			JsonNode node = mapper.readTree(body.string());
			JsonNode contactsNode = node.get("contacts");
			if (contactsNode == null || !contactsNode.isArray()) {
				throw new ApiException();
			}
			List<ContactId> list = new ArrayList<>();
			for (JsonNode contactNode : contactsNode) {
				if (!contactNode.isNumber()) throw new ApiException();
				int id = contactNode.intValue();
				if (id < 1) throw new ApiException();
				list.add(new ContactId(id));
			}
			return list;
		} catch (JacksonException e) {
			throw new ApiException();
		}
	}

	private Request.Builder getRequestBuilder(String token) {
		return new Request.Builder()
				.addHeader("Authorization", "Bearer " + token);
	}

}
