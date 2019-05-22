package org.briarproject.bramble.contact;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.UnsupportedVersionException;
import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyParser;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.util.Base32;
import org.jmock.Expectations;
import org.junit.Test;

import java.security.GeneralSecurityException;

import static java.lang.System.arraycopy;
import static org.briarproject.bramble.api.contact.HandshakeLinkConstants.BASE32_LINK_BYTES;
import static org.briarproject.bramble.api.contact.HandshakeLinkConstants.FORMAT_VERSION;
import static org.briarproject.bramble.api.contact.HandshakeLinkConstants.ID_LABEL;
import static org.briarproject.bramble.api.contact.HandshakeLinkConstants.RAW_LINK_BYTES;
import static org.briarproject.bramble.api.crypto.CryptoConstants.KEY_TYPE_AGREEMENT;
import static org.briarproject.bramble.api.crypto.CryptoConstants.KEY_TYPE_SIGNATURE;
import static org.briarproject.bramble.api.crypto.CryptoConstants.MAX_AGREEMENT_PUBLIC_KEY_BYTES;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.test.TestUtils.getAgreementPublicKey;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class PendingContactFactoryImplTest extends BrambleMockTestCase {

	private final CryptoComponent crypto = context.mock(CryptoComponent.class);
	private final Clock clock = context.mock(Clock.class);
	private final KeyParser keyParser = context.mock(KeyParser.class);

	private final PendingContactFactory pendingContactFactory =
			new PendingContactFactoryImpl(crypto, clock);
	private final String alias = getRandomString(MAX_AUTHOR_NAME_LENGTH);
	private final PublicKey publicKey = getAgreementPublicKey();
	private final byte[] idBytes = getRandomId();
	private final long timestamp = System.currentTimeMillis();

	@Test(expected = FormatException.class)
	public void testRejectsSyntacticallyInvalidLink() throws Exception {
		pendingContactFactory.createPendingContact("briar://potato", alias);
	}

	@Test
	public void testRejectsLinkWithUnknownFormatVersion() throws Exception {
		String link = encodeLink(FORMAT_VERSION + 1);
		try {
			pendingContactFactory.createPendingContact(link, alias);
			fail();
		} catch (UnsupportedVersionException e) {
			assertFalse(e.isTooOld());
		}
	}

	@Test(expected = FormatException.class)
	public void testRejectsLinkWithInvalidPublicKey() throws Exception {
		context.checking(new Expectations() {{
			oneOf(crypto).getAgreementKeyParser();
			will(returnValue(keyParser));
			oneOf(keyParser).parsePublicKey(publicKey.getEncoded());
			will(throwException(new GeneralSecurityException()));
		}});

		pendingContactFactory.createPendingContact(encodeLink(), alias);
	}

	@Test
	public void testAcceptsValidLinkWithoutPrefix() throws Exception {
		testAcceptsValidLink(encodeLink());
	}

	@Test
	public void testAcceptsValidLinkWithPrefix() throws Exception {
		testAcceptsValidLink("briar://" + encodeLink());
	}

	@Test
	public void testAcceptsValidLinkWithRubbish() throws Exception {
		testAcceptsValidLink("before " + encodeLink() + " after");
	}

	@Test
	public void testAcceptsValidLinkWithPrefixAndRubbish() throws Exception {
		testAcceptsValidLink("before briar://" + encodeLink() + " after");
	}

	private void testAcceptsValidLink(String link) throws Exception {
		context.checking(new Expectations() {{
			oneOf(crypto).getAgreementKeyParser();
			will(returnValue(keyParser));
			oneOf(keyParser).parsePublicKey(publicKey.getEncoded());
			will(returnValue(publicKey));
			oneOf(crypto).hash(ID_LABEL, publicKey.getEncoded());
			will(returnValue(idBytes));
			oneOf(clock).currentTimeMillis();
			will(returnValue(timestamp));
		}});

		PendingContact p =
				pendingContactFactory.createPendingContact(link, alias);
		assertArrayEquals(idBytes, p.getId().getBytes());
		assertArrayEquals(publicKey.getEncoded(),
				p.getPublicKey().getEncoded());
		assertEquals(alias, p.getAlias());
		assertEquals(timestamp, p.getTimestamp());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCreateHandshakeLinkRejectsInvalidKeyType() {
		PublicKey invalidPublicKey = context.mock(PublicKey.class);

		context.checking(new Expectations() {{
			oneOf(invalidPublicKey).getKeyType();
			will(returnValue(KEY_TYPE_SIGNATURE));
		}});

		pendingContactFactory.createHandshakeLink(invalidPublicKey);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCreateHandshakeLinkRejectsInvalidKeyLength() {
		PublicKey invalidPublicKey = context.mock(PublicKey.class);
		byte[] invalidPublicKeyBytes =
				getRandomBytes(MAX_AGREEMENT_PUBLIC_KEY_BYTES + 1);

		context.checking(new Expectations() {{
			oneOf(invalidPublicKey).getKeyType();
			will(returnValue(KEY_TYPE_AGREEMENT));
			oneOf(invalidPublicKey).getEncoded();
			will(returnValue(invalidPublicKeyBytes));
		}});

		pendingContactFactory.createHandshakeLink(invalidPublicKey);
	}

	@Test
	public void testCreateAndParseLink() throws Exception {
		context.checking(new Expectations() {{
			oneOf(crypto).getAgreementKeyParser();
			will(returnValue(keyParser));
			oneOf(keyParser).parsePublicKey(publicKey.getEncoded());
			will(returnValue(publicKey));
			oneOf(crypto).hash(ID_LABEL, publicKey.getEncoded());
			will(returnValue(idBytes));
			oneOf(clock).currentTimeMillis();
			will(returnValue(timestamp));
		}});

		String link = pendingContactFactory.createHandshakeLink(publicKey);
		PendingContact p =
				pendingContactFactory.createPendingContact(link, alias);
		assertArrayEquals(idBytes, p.getId().getBytes());
		assertArrayEquals(publicKey.getEncoded(),
				p.getPublicKey().getEncoded());
		assertEquals(alias, p.getAlias());
		assertEquals(timestamp, p.getTimestamp());
	}

	private String encodeLink() {
		return encodeLink(FORMAT_VERSION);
	}

	private String encodeLink(int formatVersion) {
		byte[] rawLink = new byte[RAW_LINK_BYTES];
		rawLink[0] = (byte) formatVersion;
		byte[] publicKeyBytes = publicKey.getEncoded();
		arraycopy(publicKeyBytes, 0, rawLink, 1, publicKeyBytes.length);
		String base32 = Base32.encode(rawLink).toLowerCase();
		assertEquals(BASE32_LINK_BYTES, base32.length());
		return base32;
	}
}
