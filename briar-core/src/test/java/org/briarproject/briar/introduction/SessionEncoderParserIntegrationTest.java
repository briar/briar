package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.transport.KeySetId;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.introduction.IntroducerSession.Introducee;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.briarproject.briar.test.DaggerBriarIntegrationTestComponent;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getTransportId;
import static org.briarproject.bramble.test.TestUtils.getTransportPropertiesMap;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.briar.introduction.IntroduceeState.LOCAL_ACCEPTED;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_AUTHS;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_ROLE;
import static org.briarproject.briar.api.introduction.Role.INTRODUCEE;
import static org.briarproject.briar.api.introduction.Role.INTRODUCER;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SessionEncoderParserIntegrationTest extends BrambleTestCase {

	@Inject
	ClientHelper clientHelper;
	@Inject
	AuthorFactory authorFactory;

	private final SessionEncoder sessionEncoder;
	private final SessionParser sessionParser;

	private final GroupId groupId1 = new GroupId(getRandomId());
	private final GroupId groupId2 = new GroupId(getRandomId());
	private final SessionId sessionId = new SessionId(getRandomId());
	private final long requestTimestamp = 42;
	private final long localTimestamp = 1337;
	private final long localTimestamp2 = 1338;
	private final long acceptTimestamp = 123456;
	private final long remoteAcceptTimestamp = 1234567;
	private final MessageId lastLocalMessageId = new MessageId(getRandomId());
	private final MessageId lastLocalMessageId2 = new MessageId(getRandomId());
	private final MessageId lastRemoteMessageId = new MessageId(getRandomId());
	private final MessageId lastRemoteMessageId2 = new MessageId(getRandomId());
	private final Author author1;
	private final Author author2;
	private final byte[] ephemeralPublicKey =
			getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
	private final byte[] ephemeralPrivateKey =
			getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
	private final byte[] masterKey = getRandomBytes(SecretKey.LENGTH);
	private final byte[] remoteEphemeralPublicKey =
			getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
	private final Map<TransportId, TransportProperties> transportProperties =
			getTransportPropertiesMap(3);
	private final Map<TransportId, TransportProperties>
			remoteTransportProperties = getTransportPropertiesMap(3);
	private final Map<TransportId, KeySetId> transportKeys = new HashMap<>();

	public SessionEncoderParserIntegrationTest() {
		BriarIntegrationTestComponent component =
				DaggerBriarIntegrationTestComponent.builder().build();
		component.inject(this);

		sessionEncoder = new SessionEncoderImpl(clientHelper);
		sessionParser = new SessionParserImpl(clientHelper);
		author1 = getRealAuthor();
		author2 = getRealAuthor();
		transportKeys.put(getTransportId(), new KeySetId(1));
		transportKeys.put(getTransportId(), new KeySetId(2));
		transportKeys.put(getTransportId(), new KeySetId(3));
	}

	@Test
	public void testIntroducerSession() throws FormatException {
		IntroducerSession s1 = getIntroducerSession();

		BdfDictionary d = sessionEncoder.encodeIntroducerSession(s1);
		IntroducerSession s2 = sessionParser.parseIntroducerSession(d);

		assertEquals(INTRODUCER, s1.getRole());
		assertEquals(s1.getRole(), s2.getRole());
		assertEquals(sessionId, s1.getSessionId());
		assertEquals(s1.getSessionId(), s2.getSessionId());
		assertEquals(AWAIT_AUTHS, s1.getState());
		assertEquals(s1.getState(), s2.getState());
		assertIntroduceeEquals(s1.getIntroducee1(), s2.getIntroducee1());
		assertIntroduceeEquals(s1.getIntroducee2(), s2.getIntroducee2());
	}

	@Test
	public void testIntroducerSessionWithNulls() throws FormatException {
		Introducee introducee1 =
				new Introducee(sessionId, groupId1, author1, localTimestamp,
						null, null);
		Introducee introducee2 =
				new Introducee(sessionId, groupId2, author2, localTimestamp2,
						null, null);
		IntroducerSession s1 = new IntroducerSession(sessionId,
				AWAIT_AUTHS, requestTimestamp, introducee1,
				introducee2);

		BdfDictionary d = sessionEncoder.encodeIntroducerSession(s1);
		IntroducerSession s2 = sessionParser.parseIntroducerSession(d);

		assertNull(s1.getIntroducee1().lastLocalMessageId);
		assertEquals(s1.getIntroducee1().lastLocalMessageId,
				s2.getIntroducee1().lastLocalMessageId);
		assertNull(s1.getIntroducee1().lastRemoteMessageId);
		assertEquals(s1.getIntroducee1().lastRemoteMessageId,
				s2.getIntroducee1().lastRemoteMessageId);

		assertNull(s1.getIntroducee2().lastLocalMessageId);
		assertEquals(s1.getIntroducee2().lastLocalMessageId,
				s2.getIntroducee2().lastLocalMessageId);
		assertNull(s1.getIntroducee2().lastRemoteMessageId);
		assertEquals(s1.getIntroducee2().lastRemoteMessageId,
				s2.getIntroducee2().lastRemoteMessageId);
	}

	@Test(expected = FormatException.class)
	public void testIntroducerSessionUnknownRole() throws FormatException {
		IntroducerSession s = getIntroducerSession();
		BdfDictionary d = sessionEncoder.encodeIntroducerSession(s);
		d.put(SESSION_KEY_ROLE, 1337);
		sessionParser.parseIntroducerSession(d);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIntroducerSessionWrongRole() throws FormatException {
		IntroducerSession s = getIntroducerSession();
		BdfDictionary d = sessionEncoder.encodeIntroducerSession(s);
		d.put(SESSION_KEY_ROLE, INTRODUCEE.getValue());
		sessionParser.parseIntroducerSession(d);
	}

	@Test
	public void testIntroduceeSession() throws FormatException {
		IntroduceeSession s1 = getIntroduceeSession();
		BdfDictionary d = sessionEncoder.encodeIntroduceeSession(s1);
		IntroduceeSession s2 =
				sessionParser.parseIntroduceeSession(groupId1, d);

		assertEquals(LOCAL_ACCEPTED, s1.getState());
		assertEquals(s1.getState(), s2.getState());
		assertEquals(INTRODUCEE, s1.getRole());
		assertEquals(s1.getRole(), s2.getRole());
		assertEquals(sessionId, s1.getSessionId());
		assertEquals(s1.getSessionId(), s2.getSessionId());
		assertEquals(groupId1, s1.getContactGroupId());
		assertEquals(s1.getContactGroupId(), s2.getContactGroupId());
		assertEquals(localTimestamp, s1.getLocalTimestamp());
		assertEquals(s1.getLocalTimestamp(), s2.getLocalTimestamp());
		assertEquals(lastLocalMessageId, s1.getLastLocalMessageId());
		assertEquals(s1.getLastLocalMessageId(), s2.getLastLocalMessageId());
		assertEquals(lastRemoteMessageId, s1.getLastRemoteMessageId());
		assertEquals(s1.getLastRemoteMessageId(), s2.getLastRemoteMessageId());
		assertEquals(author1, s1.getIntroducer());
		assertEquals(s1.getIntroducer(), s2.getIntroducer());
		assertEquals(author2, s1.getRemoteAuthor());
		assertEquals(s1.getRemoteAuthor(), s2.getRemoteAuthor());
		assertArrayEquals(ephemeralPublicKey, s1.getEphemeralPublicKey());
		assertArrayEquals(s1.getEphemeralPublicKey(),
				s2.getEphemeralPublicKey());
		assertArrayEquals(ephemeralPrivateKey, s1.getEphemeralPrivateKey());
		assertArrayEquals(s1.getEphemeralPrivateKey(),
				s2.getEphemeralPrivateKey());
		assertEquals(acceptTimestamp, s1.getAcceptTimestamp());
		assertEquals(s1.getAcceptTimestamp(), s2.getAcceptTimestamp());
		assertArrayEquals(masterKey, s1.getMasterKey());
		assertArrayEquals(s1.getMasterKey(), s2.getMasterKey());
		assertArrayEquals(remoteEphemeralPublicKey, s1.getRemotePublicKey());
		assertArrayEquals(s1.getRemotePublicKey(),
				s2.getRemotePublicKey());
		assertEquals(transportProperties, s1.getTransportProperties());
		assertEquals(s1.getTransportProperties(), s2.getTransportProperties());
		assertEquals(remoteTransportProperties,
				s1.getRemoteTransportProperties());
		assertEquals(s1.getRemoteTransportProperties(),
				s2.getRemoteTransportProperties());
		assertEquals(remoteAcceptTimestamp, s1.getRemoteAcceptTimestamp());
		assertEquals(s1.getRemoteAcceptTimestamp(), s2.getRemoteAcceptTimestamp());
		assertEquals(transportKeys, s1.getTransportKeys());
		assertEquals(s1.getTransportKeys(), s2.getTransportKeys());
	}

	@Test
	public void testIntroduceeSessionWithNulls() throws FormatException {
		IntroduceeSession s1 =
				new IntroduceeSession(sessionId, LOCAL_ACCEPTED,
						requestTimestamp, groupId1, null, localTimestamp, null,
						author1, null, null, null, acceptTimestamp, null,
						author2, null, null, remoteAcceptTimestamp, null);

		BdfDictionary d = sessionEncoder.encodeIntroduceeSession(s1);
		IntroduceeSession s2 =
				sessionParser.parseIntroduceeSession(groupId1, d);

		assertNull(s1.getLastLocalMessageId());
		assertEquals(s1.getLastLocalMessageId(), s2.getLastLocalMessageId());
		assertNull(s1.getLastRemoteMessageId());
		assertEquals(s1.getLastRemoteMessageId(), s2.getLastRemoteMessageId());
		assertNull(s1.getEphemeralPublicKey());
		assertArrayEquals(s1.getEphemeralPublicKey(),
				s2.getEphemeralPublicKey());
		assertNull(s1.getEphemeralPrivateKey());
		assertArrayEquals(s1.getEphemeralPrivateKey(),
				s2.getEphemeralPrivateKey());
		assertNull(s1.getTransportKeys());
		assertEquals(s1.getTransportKeys(), s2.getTransportKeys());
	}

	@Test(expected = FormatException.class)
	public void testIntroduceeSessionUnknownRole() throws FormatException {
		IntroduceeSession s = getIntroduceeSession();
		BdfDictionary d = sessionEncoder.encodeIntroduceeSession(s);
		d.put(SESSION_KEY_ROLE, 1337);
		sessionParser.parseIntroduceeSession(groupId1, d);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIntroduceeSessionWrongRole() throws FormatException {
		IntroduceeSession s = getIntroduceeSession();
		BdfDictionary d = sessionEncoder.encodeIntroduceeSession(s);
		d.put(SESSION_KEY_ROLE, INTRODUCER.getValue());
		sessionParser.parseIntroduceeSession(groupId1, d);
	}

	private IntroducerSession getIntroducerSession() {
		Introducee introducee1 =
				new Introducee(sessionId, groupId1, author1, localTimestamp,
						lastLocalMessageId, lastRemoteMessageId);
		Introducee introducee2 =
				new Introducee(sessionId, groupId2, author2, localTimestamp2,
						lastLocalMessageId2, lastRemoteMessageId2);
		return new IntroducerSession(sessionId, AWAIT_AUTHS,
				requestTimestamp, introducee1, introducee2);
	}

	private IntroduceeSession getIntroduceeSession() {
		return new IntroduceeSession(sessionId, LOCAL_ACCEPTED,
				requestTimestamp, groupId1, lastLocalMessageId, localTimestamp,
				lastRemoteMessageId, author1, ephemeralPublicKey,
				ephemeralPrivateKey, transportProperties, acceptTimestamp,
				masterKey, author2, remoteEphemeralPublicKey,
				remoteTransportProperties, remoteAcceptTimestamp,
				transportKeys);
	}

	private void assertIntroduceeEquals(Introducee i1, Introducee i2) {
		assertEquals(i1.author, i2.author);
		assertEquals(i1.groupId, i2.groupId);
		assertEquals(i1.localTimestamp, i2.localTimestamp);
		assertEquals(i1.lastLocalMessageId, i2.lastLocalMessageId);
		assertEquals(i1.lastRemoteMessageId, i2.lastRemoteMessageId);
	}

	private Author getRealAuthor() {
		return authorFactory.createAuthor(getRandomString(5),
				getRandomBytes(MAX_PUBLIC_KEY_LENGTH));
	}

}
