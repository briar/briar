package org.briarproject.properties;

import org.briarproject.BriarTestCase;
import org.junit.Test;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.briarproject.api.data.BdfWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;

import org.briarproject.properties.TransportPropertyValidator;
import org.briarproject.api.properties.TransportProperties;
import org.briarproject.api.TransportId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.UniqueId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.DeviceId;
import org.briarproject.api.data.BdfWriter;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.BdfReader;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.system.Clock;
import org.briarproject.api.DeviceId;
import org.briarproject.api.FormatException;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.Message;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.TestUtils;
import org.briarproject.util.StringUtils;

import static org.briarproject.api.TransportId.MAX_TRANSPORT_ID_LENGTH;
import static org.briarproject.api.properties.TransportPropertyConstants.MAX_PROPERTIES_PER_TRANSPORT;

import org.jmock.Expectations;
import org.jmock.Mockery;

public class TransportPropertyValidatorTest extends BriarTestCase {

	private TransportId transportId;
	private MessageId messageId;
	private GroupId groupId;
	private ClientId clientId;
	private DeviceId deviceId;

	private final ClientHelper h;
	private final MetadataEncoder e;
	private final Clock c;
	private final Group g;
	private final TransportPropertyValidator tpv;
	private final BdfDictionary d;

    public TransportPropertyValidatorTest () {
		transportId = new TransportId("test");
		messageId = new MessageId(TestUtils.getRandomId());
		groupId = new GroupId(TestUtils.getRandomId());
		clientId = new ClientId(TestUtils.getRandomId());
		deviceId = new DeviceId(TestUtils.getRandomId());

		Mockery context = new Mockery();
		h = context.mock(ClientHelper.class);
		e = context.mock(MetadataEncoder.class);
		c = context.mock(Clock.class);
		g = new Group(groupId, clientId, TestUtils.getRandomBytes(12));
		d = new BdfDictionary();
		tpv = new TransportPropertyValidator(
				h, e, c);
    }

    @Test
    public void testValidateProperMessage() throws IOException {

		BdfDictionary result;
		final BdfList m = BdfList.of(deviceId, transportId.getString(), 4, d);

		result = tpv.validateMessage(m, g, 1L);

		assertEquals("test", result.getString("transportId"));
		assertEquals(result.getLong("version").longValue(), 4);
	}

    @Test(expected = FormatException.class)
    public void testValidateWrongVersionValue() throws IOException {

		/* Will create a negative version number */
		BdfList m = BdfList.of(deviceId, transportId.getString(), -1, d);
		tpv.validateMessage(m, g, 1L);
	}

    @Test(expected = FormatException.class)
    public void testValidateWrongVersionType() throws IOException {

		/* Instead of sending a version number I'm sending a dict */
		BdfList m = BdfList.of(deviceId, transportId.getString(), d, d);
		tpv.validateMessage(m, g, 1L);
	}

    @Test(expected = FormatException.class)
    public void testValidateShortDeviceId() throws IOException {

		/* Will create a Device Id with a short length, getRaw should work */
		BdfList m = BdfList.of(new byte[UniqueId.LENGTH-1],
				transportId.getString(), 1, d);
		tpv.validateMessage(m, g, 1L);
	}

    @Test(expected = FormatException.class)
	public void testValidateLongDeviceId() throws IOException {

		BdfList m = BdfList.of(new byte[UniqueId.LENGTH+1],
				transportId.getString(), 1, d);
		tpv.validateMessage(m, g, 1L);
	}

    @Test(expected = FormatException.class)
	public void testValidateWrongDeviceId() throws IOException {

		BdfList m = BdfList.of(d, transportId.getString(), 1, d);
		tpv.validateMessage(m, g, 1L);
	}

    @Test(expected =FormatException.class)
    public void testValidateLongTransportId() throws IOException {

		/* Generate a string or arbitrary length for the transport id*/
		String wrongTransportIdString = TestUtils.createRandomString(MAX_TRANSPORT_ID_LENGTH + 1);
		BdfList m = BdfList.of(deviceId, wrongTransportIdString, 4, d);

		tpv.validateMessage(m, g, 1L);
	}

    @Test(expected = FormatException.class)
    public void testValidateTooManyProperties() throws IOException {

		/* Generate a big map for the BdfDictionary*/
		HashMap map = new HashMap();
		for (int i = 0; i < MAX_PROPERTIES_PER_TRANSPORT + 1; i++)
			map.put("" + i, "" + i);
		BdfDictionary d = new BdfDictionary(map);

		BdfList m = BdfList.of(deviceId, transportId.getString(), 4, d);

		tpv.validateMessage(m, g, 1L);
	}
}
