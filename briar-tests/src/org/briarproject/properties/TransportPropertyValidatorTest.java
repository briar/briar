package org.briarproject.properties;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.FormatException;
import org.briarproject.api.TransportId;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;
import org.jmock.Mockery;
import org.junit.Test;

import java.io.IOException;

import static org.briarproject.api.TransportId.MAX_TRANSPORT_ID_LENGTH;
import static org.briarproject.api.properties.TransportPropertyConstants.MAX_PROPERTIES_PER_TRANSPORT;
import static org.junit.Assert.assertEquals;

public class TransportPropertyValidatorTest extends BriarTestCase {

	private final TransportId transportId;
	private final BdfDictionary bdfDictionary;
	private final Group group;
	private final Message message;
	private final TransportPropertyValidator tpv;

	public TransportPropertyValidatorTest() {
		transportId = new TransportId("test");
		bdfDictionary = new BdfDictionary();

		GroupId groupId = new GroupId(TestUtils.getRandomId());
		ClientId clientId = new ClientId(TestUtils.getRandomId());
		byte[] descriptor = TestUtils.getRandomBytes(12);
		group = new Group(groupId, clientId, descriptor);

		MessageId messageId = new MessageId(TestUtils.getRandomId());
		long timestamp = System.currentTimeMillis();
		byte[] body = TestUtils.getRandomBytes(123);
		message = new Message(messageId, groupId, timestamp, body);

		Mockery context = new Mockery();
		ClientHelper clientHelper = context.mock(ClientHelper.class);
		MetadataEncoder metadataEncoder = context.mock(MetadataEncoder.class);
		Clock clock = context.mock(Clock.class);

		tpv = new TransportPropertyValidator(clientHelper, metadataEncoder,
				clock);
	}

	@Test
	public void testValidateProperMessage() throws IOException {

		BdfList body = BdfList.of(transportId.getString(), 4, bdfDictionary);

		BdfDictionary result = tpv.validateMessage(message, group, body);

		assertEquals("test", result.getString("transportId"));
		assertEquals(4, result.getLong("version").longValue());
	}

	@Test(expected = FormatException.class)
	public void testValidateWrongVersionValue() throws IOException {

		BdfList body = BdfList.of(transportId.getString(), -1, bdfDictionary);
		tpv.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testValidateWrongVersionType() throws IOException {

		BdfList body = BdfList.of(transportId.getString(), bdfDictionary,
				bdfDictionary);
		tpv.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testValidateLongTransportId() throws IOException {

		String wrongTransportIdString =
				TestUtils.getRandomString(MAX_TRANSPORT_ID_LENGTH + 1);
		BdfList body = BdfList.of(wrongTransportIdString, 4, bdfDictionary);
		tpv.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testValidateEmptyTransportId() throws IOException {

		BdfList body = BdfList.of("", 4, bdfDictionary);
		tpv.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testValidateTooManyProperties() throws IOException {

		BdfDictionary d = new BdfDictionary();
		for (int i = 0; i < MAX_PROPERTIES_PER_TRANSPORT + 1; i++)
			d.put(String.valueOf(i), i);
		BdfList body = BdfList.of(transportId.getString(), 4, d);
		tpv.validateMessage(message, group, body);
	}
}
