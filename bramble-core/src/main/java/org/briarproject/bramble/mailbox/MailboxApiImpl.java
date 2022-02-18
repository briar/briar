package org.briarproject.bramble.mailbox;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.briarproject.bramble.api.WeakSingletonProvider;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.mailbox.InvalidMailboxIdException;
import org.briarproject.bramble.api.mailbox.MailboxAuthToken;
import org.briarproject.bramble.api.mailbox.MailboxFileId;
import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.api.mailbox.MailboxId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import static org.briarproject.bramble.util.IoUtils.copyAndClose;

@NotNullByDefault
class MailboxApiImpl implements MailboxApi {

	private final WeakSingletonProvider<OkHttpClient> httpClientProvider;
	private final JsonMapper mapper = JsonMapper.builder()
			.enable(BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)
			.build();
	private static final MediaType JSON =
			requireNonNull(MediaType.parse("application/json; charset=utf-8"));
	private static final MediaType FILE =
			requireNonNull(MediaType.parse("application/octet-stream"));

	@Inject
	MailboxApiImpl(WeakSingletonProvider<OkHttpClient> httpClientProvider) {
		this.httpClientProvider = httpClientProvider;
	}

	@Override
	public MailboxAuthToken setup(MailboxProperties properties)
			throws IOException, ApiException {
		if (!properties.isOwner()) throw new IllegalArgumentException();
		Request request = getRequestBuilder(properties.getAuthToken())
				.url(properties.getBaseUrl() + "/setup")
				.put(EMPTY_REQUEST)
				.build();
		OkHttpClient client = httpClientProvider.get();
		Response response = client.newCall(request).execute();
		if (response.code() == 401) throw new MailboxAlreadyPairedException();
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
			return MailboxAuthToken.fromString(ownerToken);
		} catch (JacksonException | InvalidMailboxIdException e) {
			throw new ApiException();
		}
	}

	@Override
	public boolean checkStatus(MailboxProperties properties)
			throws IOException, ApiException {
		if (!properties.isOwner()) throw new IllegalArgumentException();
		Response response = sendGetRequest(properties, "/status");
		if (response.code() == 401) throw new ApiException();
		return response.isSuccessful();
	}

	@Override
	public void wipeMailbox(MailboxProperties properties)
			throws IOException, ApiException {
		if (!properties.isOwner()) throw new IllegalArgumentException();
		Request request = getRequestBuilder(properties.getAuthToken())
				.url(properties.getBaseUrl() + "/")
				.delete()
				.build();
		OkHttpClient client = httpClientProvider.get();
		Response response = client.newCall(request).execute();
		if (response.code() != 204) throw new ApiException();
	}

	/* Contact Management API (owner only) */

	@Override
	public void addContact(MailboxProperties properties, MailboxContact contact)
			throws IOException, ApiException, TolerableFailureException {
		if (!properties.isOwner()) throw new IllegalArgumentException();
		byte[] bodyBytes = mapper.writeValueAsBytes(contact);
		RequestBody body = RequestBody.create(JSON, bodyBytes);
		Response response = sendPostRequest(properties, "/contacts", body);
		if (response.code() == 409) throw new TolerableFailureException();
		if (!response.isSuccessful()) throw new ApiException();
	}

	@Override
	public void deleteContact(MailboxProperties properties, ContactId contactId)
			throws IOException, ApiException, TolerableFailureException {
		if (!properties.isOwner()) throw new IllegalArgumentException();
		String url = properties.getBaseUrl() + "/contacts/" +
				contactId.getInt();
		Request request = getRequestBuilder(properties.getAuthToken())
				.delete()
				.url(url)
				.build();
		OkHttpClient client = httpClientProvider.get();
		Response response = client.newCall(request).execute();
		if (response.code() == 404) throw new TolerableFailureException();
		if (response.code() != 200) throw new ApiException();
	}

	@Override
	public Collection<ContactId> getContacts(MailboxProperties properties)
			throws IOException, ApiException {
		if (!properties.isOwner()) throw new IllegalArgumentException();
		Response response = sendGetRequest(properties, "/contacts");
		if (response.code() != 200) throw new ApiException();

		ResponseBody body = response.body();
		if (body == null) throw new ApiException();
		try {
			JsonNode node = mapper.readTree(body.string());
			ArrayNode contactsNode = getArray(node, "contacts");
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

	/* File Management (owner and contacts) */

	@Override
	public void addFile(MailboxProperties properties, MailboxFolderId folderId,
			File file) throws IOException, ApiException {
		String path = "/files/" + folderId;
		RequestBody body = RequestBody.create(FILE, file);
		Response response = sendPostRequest(properties, path, body);
		if (response.code() != 200) throw new ApiException();
	}

	@Override
	public List<MailboxFile> getFiles(MailboxProperties properties,
			MailboxFolderId folderId) throws IOException, ApiException {
		String path = "/files/" + folderId;
		Response response = sendGetRequest(properties, path);
		if (response.code() != 200) throw new ApiException();

		ResponseBody body = response.body();
		if (body == null) throw new ApiException();
		try {
			JsonNode node = mapper.readTree(body.string());
			ArrayNode filesNode = getArray(node, "files");
			List<MailboxFile> list = new ArrayList<>();
			for (JsonNode fileNode : filesNode) {
				if (!fileNode.isObject()) throw new ApiException();
				ObjectNode objectNode = (ObjectNode) fileNode;
				JsonNode nameNode = objectNode.get("name");
				JsonNode timeNode = objectNode.get("time");
				if (nameNode == null || !nameNode.isTextual()) {
					throw new ApiException();
				}
				if (timeNode == null || !timeNode.isNumber()) {
					throw new ApiException();
				}
				String name = nameNode.asText();
				long time = timeNode.asLong();
				if (time < 1) throw new ApiException();
				list.add(new MailboxFile(MailboxFileId.fromString(name), time));
			}
			Collections.sort(list);
			return list;
		} catch (JacksonException | InvalidMailboxIdException e) {
			throw new ApiException();
		}
	}

	@Override
	public void getFile(MailboxProperties properties, MailboxFolderId folderId,
			MailboxFileId fileId, File file) throws IOException, ApiException {
		String path = "/files/" + folderId + "/" + fileId;
		Response response = sendGetRequest(properties, path);
		if (response.code() != 200) throw new ApiException();

		ResponseBody body = response.body();
		if (body == null) throw new ApiException();
		FileOutputStream outputStream = new FileOutputStream(file);
		copyAndClose(body.byteStream(), outputStream);
	}

	@Override
	public void deleteFile(MailboxProperties properties,
			MailboxFolderId folderId, MailboxFileId fileId)
			throws IOException, ApiException, TolerableFailureException {
		String path = "/files/" + folderId + "/" + fileId;
		Request request = getRequestBuilder(properties.getAuthToken())
				.delete()
				.url(properties.getBaseUrl() + path)
				.build();
		OkHttpClient client = httpClientProvider.get();
		Response response = client.newCall(request).execute();
		if (response.code() == 404) throw new TolerableFailureException();
		if (response.code() != 200) throw new ApiException();
	}

	@Override
	public List<MailboxFolderId> getFolders(MailboxProperties properties)
			throws IOException, ApiException {
		if (!properties.isOwner()) throw new IllegalArgumentException();
		Response response = sendGetRequest(properties, "/folders");
		if (response.code() != 200) throw new ApiException();

		ResponseBody body = response.body();
		if (body == null) throw new ApiException();
		try {
			JsonNode node = mapper.readTree(body.string());
			ArrayNode filesNode = getArray(node, "folders");
			List<MailboxFolderId> list = new ArrayList<>();
			for (JsonNode fileNode : filesNode) {
				if (!fileNode.isObject()) throw new ApiException();
				ObjectNode objectNode = (ObjectNode) fileNode;
				JsonNode idNode = objectNode.get("id");
				if (idNode == null || !idNode.isTextual()) {
					throw new ApiException();
				}
				String id = idNode.asText();
				list.add(MailboxFolderId.fromString(id));
			}
			return list;
		} catch (JacksonException | InvalidMailboxIdException e) {
			throw new ApiException();
		}
	}

	/* Helper Functions */

	private Response sendGetRequest(MailboxProperties properties, String path)
			throws IOException {
		Request request = getRequestBuilder(properties.getAuthToken())
				.url(properties.getBaseUrl() + path)
				.build();
		OkHttpClient client = httpClientProvider.get();
		return client.newCall(request).execute();
	}

	private Response sendPostRequest(MailboxProperties properties, String path,
			RequestBody body) throws IOException {
		Request request = getRequestBuilder(properties.getAuthToken())
				.url(properties.getBaseUrl() + path)
				.post(body)
				.build();
		OkHttpClient client = httpClientProvider.get();
		return client.newCall(request).execute();
	}

	private Request.Builder getRequestBuilder(MailboxId token) {
		return new Request.Builder()
				.addHeader("Authorization", "Bearer " + token);
	}

	/* JSON helpers */

	private ArrayNode getArray(JsonNode node, String name) throws ApiException {
		JsonNode arrayNode = node.get(name);
		if (arrayNode == null || !arrayNode.isArray()) {
			throw new ApiException();
		}
		return (ArrayNode) arrayNode;
	}

}
