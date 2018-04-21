package org.briarproject.briar.introduction2;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.briarproject.briar.test.DaggerBriarIntegrationTestComponent;
import org.junit.Test;

import java.util.Map;

import javax.inject.Inject;

import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getTransportPropertiesMap;
import static org.briarproject.bramble.util.StringUtils.fromHexString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IntroductionCryptoImplTest extends BrambleTestCase {

	@Inject
	ClientHelper clientHelper;
	@Inject
	AuthorFactory authorFactory;
	@Inject
	CryptoComponent cryptoComponent;

	private final IntroductionCryptoImpl crypto;

	private final Author introducer;
	private final LocalAuthor alice, bob;
	private final long aliceAcceptTimestamp = 42L;
	private final long bobAcceptTimestamp = 1337L;
	private final SecretKey masterKey =
			new SecretKey(getRandomBytes(SecretKey.LENGTH));
	private final KeyPair aliceEphemeral, bobEphemeral;
	private final Map<TransportId, TransportProperties> aliceTransport =
			getTransportPropertiesMap(3);
	private final Map<TransportId, TransportProperties> bobTransport =
			getTransportPropertiesMap(3);

	public IntroductionCryptoImplTest() {
		BriarIntegrationTestComponent component =
				DaggerBriarIntegrationTestComponent.builder().build();
		component.inject(this);
		crypto = new IntroductionCryptoImpl(cryptoComponent, clientHelper);

		// create actual deterministic authors for testing
		introducer = authorFactory
				.createAuthor("Introducer", new byte[] {0x1, 0x2, 0x3});
		alice = authorFactory.createLocalAuthor("Alice",
				fromHexString(
						"A626F080C94771698F86B4B4094C4F560904B53398805AE02BA2343F1829187A"),
				fromHexString(
						"60F010187AF91ACA15141E8C811EC8E79C7CAA6461C21A852BB03066C89B0A70"));
		bob = authorFactory.createLocalAuthor("Bob",
				fromHexString(
						"A0D0FED1CE4674D8B6441AD0A664E41BF60D489F35DA11F52AF923540848546F"),
				fromHexString(
						"20B25BE7E999F68FE07189449E91984FA79121DBFF28A651669A3CF512D6A758"));
		aliceEphemeral = crypto.generateKeyPair();
		bobEphemeral = crypto.generateKeyPair();
	}

	@Test
	public void testGetSessionId() {
		SessionId s1 = crypto.getSessionId(introducer, alice, bob);
		SessionId s2 = crypto.getSessionId(introducer, bob, alice);
		assertEquals(s1, s2);
	}

	@Test
	public void testIsAlice() {
		assertTrue(crypto.isAlice(alice.getId(), bob.getId()));
		assertFalse(crypto.isAlice(bob.getId(), alice.getId()));
	}

	@Test
	public void testDeriveMasterKey() throws Exception {
		SecretKey aliceMasterKey = crypto.deriveMasterKey(alice.getPublicKey(),
				alice.getPrivateKey(), bob.getPublicKey(), true);
		SecretKey bobMasterKey = crypto.deriveMasterKey(bob.getPublicKey(),
				bob.getPrivateKey(), alice.getPublicKey(), false);
		assertArrayEquals(aliceMasterKey.getBytes(), bobMasterKey.getBytes());
	}

	@Test
	public void testAliceMac() throws Exception {
		SecretKey aliceMacKey = crypto.deriveMacKey(masterKey, true);
		byte[] aliceMac =
				crypto.mac(aliceMacKey, introducer.getId(), alice.getId(),
						bob.getId(), aliceAcceptTimestamp, bobAcceptTimestamp,
						aliceEphemeral.getPublic().getEncoded(),
						bobEphemeral.getPublic().getEncoded(), aliceTransport,
						bobTransport, true);

		crypto.verifyMac(aliceMac, masterKey, introducer.getId(), bob.getId(),
				alice.getId(), bobAcceptTimestamp, aliceAcceptTimestamp,
				bobEphemeral.getPublic().getEncoded(),
				aliceEphemeral.getPublic().getEncoded(), bobTransport,
				aliceTransport, true);
	}

	@Test
	public void testBobMac() throws Exception {
		SecretKey bobMacKey = crypto.deriveMacKey(masterKey, false);
		byte[] bobMac =
				crypto.mac(bobMacKey, introducer.getId(), bob.getId(),
						alice.getId(), bobAcceptTimestamp, aliceAcceptTimestamp,
						bobEphemeral.getPublic().getEncoded(),
						aliceEphemeral.getPublic().getEncoded(), bobTransport,
						aliceTransport, false);

		crypto.verifyMac(bobMac, masterKey, introducer.getId(), alice.getId(),
				bob.getId(), aliceAcceptTimestamp, bobAcceptTimestamp,
				aliceEphemeral.getPublic().getEncoded(),
				bobEphemeral.getPublic().getEncoded(), aliceTransport,
				bobTransport, false);
	}

	@Test
	public void testSign() throws Exception {
		KeyPair keyPair = cryptoComponent.generateSignatureKeyPair();
		SecretKey macKey = crypto.deriveMacKey(masterKey, true);
		byte[] signature =
				crypto.sign(macKey, keyPair.getPrivate().getEncoded());
		crypto.verifySignature(macKey, keyPair.getPublic().getEncoded(),
				signature);
	}

}
