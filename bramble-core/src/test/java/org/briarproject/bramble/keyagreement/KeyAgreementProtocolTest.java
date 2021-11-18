package org.briarproject.bramble.keyagreement;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyAgreementCrypto;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.KeyParser;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.keyagreement.Payload;
import org.briarproject.bramble.api.keyagreement.PayloadEncoder;
import org.briarproject.bramble.test.BrambleTestCase;
import org.jmock.Expectations;
import org.jmock.auto.Mock;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.jmock.lib.concurrent.Synchroniser;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Rule;
import org.junit.Test;

import static java.util.Collections.emptyList;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.COMMIT_LENGTH;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.MASTER_KEY_LABEL;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.PROTOCOL_VERSION;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.SHARED_SECRET_LABEL;
import static org.briarproject.bramble.test.TestUtils.getAgreementPrivateKey;
import static org.briarproject.bramble.test.TestUtils.getAgreementPublicKey;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class KeyAgreementProtocolTest extends BrambleTestCase {

	@Rule
	public JUnitRuleMockery context = new JUnitRuleMockery() {{
		// So we can mock concrete classes like KeyAgreementTransport
		setImposteriser(ClassImposteriser.INSTANCE);
		setThreadingPolicy(new Synchroniser());
	}};

	private final PublicKey alicePubKey = getAgreementPublicKey();
	private final byte[] aliceCommit = getRandomBytes(COMMIT_LENGTH);
	private final byte[] alicePayload = getRandomBytes(COMMIT_LENGTH + 8);
	private final byte[] aliceConfirm = getRandomBytes(SecretKey.LENGTH);

	private final PublicKey bobPubKey = getAgreementPublicKey();
	private final byte[] bobCommit = getRandomBytes(COMMIT_LENGTH);
	private final byte[] bobPayload = getRandomBytes(COMMIT_LENGTH + 19);
	private final byte[] bobConfirm = getRandomBytes(SecretKey.LENGTH);

	private final PublicKey badPubKey = getAgreementPublicKey();
	private final byte[] badCommit = getRandomBytes(COMMIT_LENGTH);
	private final byte[] badConfirm = getRandomBytes(SecretKey.LENGTH);

	@Mock
	KeyAgreementProtocol.Callbacks callbacks;
	@Mock
	CryptoComponent crypto;
	@Mock
	KeyAgreementCrypto keyAgreementCrypto;
	@Mock
	KeyParser keyParser;
	@Mock
	PayloadEncoder payloadEncoder;
	@Mock
	KeyAgreementTransport transport;

	@Test
	public void testAliceProtocol() throws Exception {
		// set up
		Payload theirPayload = new Payload(bobCommit, emptyList());
		Payload ourPayload = new Payload(aliceCommit, emptyList());
		KeyPair ourKeyPair = new KeyPair(alicePubKey, getAgreementPrivateKey());
		SecretKey sharedSecret = getSecretKey();
		SecretKey masterKey = getSecretKey();

		KeyAgreementProtocol protocol = new KeyAgreementProtocol(callbacks,
				crypto, keyAgreementCrypto, payloadEncoder, transport,
				theirPayload, ourPayload, ourKeyPair, true);

		// expectations
		context.checking(new Expectations() {{
			// Helpers
			allowing(payloadEncoder).encode(ourPayload);
			will(returnValue(alicePayload));
			allowing(payloadEncoder).encode(theirPayload);
			will(returnValue(bobPayload));
			allowing(crypto).getAgreementKeyParser();
			will(returnValue(keyParser));

			// Alice sends her public key
			oneOf(transport).sendKey(alicePubKey.getEncoded());

			// Alice receives Bob's public key
			oneOf(callbacks).connectionWaiting();
			oneOf(transport).receiveKey();
			will(returnValue(bobPubKey.getEncoded()));
			oneOf(callbacks).initialRecordReceived();
			oneOf(keyParser).parsePublicKey(bobPubKey.getEncoded());
			will(returnValue(bobPubKey));

			// Alice verifies Bob's public key
			oneOf(keyAgreementCrypto).deriveKeyCommitment(bobPubKey);
			will(returnValue(bobCommit));

			// Alice computes shared secret
			oneOf(crypto).deriveSharedSecret(SHARED_SECRET_LABEL, bobPubKey,
					ourKeyPair, new byte[] {PROTOCOL_VERSION},
					alicePubKey.getEncoded(), bobPubKey.getEncoded());
			will(returnValue(sharedSecret));

			// Alice sends her confirmation record
			oneOf(keyAgreementCrypto).deriveConfirmationRecord(sharedSecret,
					bobPayload, alicePayload, bobPubKey, ourKeyPair,
					true, true);
			will(returnValue(aliceConfirm));
			oneOf(transport).sendConfirm(aliceConfirm);

			// Alice receives Bob's confirmation record
			oneOf(transport).receiveConfirm();
			will(returnValue(bobConfirm));

			// Alice verifies Bob's confirmation record
			oneOf(keyAgreementCrypto).deriveConfirmationRecord(sharedSecret,
					bobPayload, alicePayload, bobPubKey, ourKeyPair,
					true, false);
			will(returnValue(bobConfirm));

			// Alice derives master key
			oneOf(crypto).deriveKey(MASTER_KEY_LABEL, sharedSecret);
			will(returnValue(masterKey));
		}});

		// execute
		assertThat(masterKey, is(equalTo(protocol.perform())));
	}

	@Test
	public void testBobProtocol() throws Exception {
		// set up
		Payload theirPayload = new Payload(aliceCommit, emptyList());
		Payload ourPayload = new Payload(bobCommit, emptyList());
		KeyPair ourKeyPair = new KeyPair(bobPubKey, getAgreementPrivateKey());
		SecretKey sharedSecret = getSecretKey();
		SecretKey masterKey = getSecretKey();

		KeyAgreementProtocol protocol = new KeyAgreementProtocol(callbacks,
				crypto, keyAgreementCrypto, payloadEncoder, transport,
				theirPayload, ourPayload, ourKeyPair, false);

		// expectations
		context.checking(new Expectations() {{
			// Helpers
			allowing(payloadEncoder).encode(ourPayload);
			will(returnValue(bobPayload));
			allowing(payloadEncoder).encode(theirPayload);
			will(returnValue(alicePayload));
			allowing(crypto).getAgreementKeyParser();
			will(returnValue(keyParser));

			// Bob receives Alice's public key
			oneOf(transport).receiveKey();
			will(returnValue(alicePubKey.getEncoded()));
			oneOf(callbacks).initialRecordReceived();
			oneOf(keyParser).parsePublicKey(alicePubKey.getEncoded());
			will(returnValue(alicePubKey));

			// Bob verifies Alice's public key
			oneOf(keyAgreementCrypto).deriveKeyCommitment(alicePubKey);
			will(returnValue(aliceCommit));

			// Bob sends his public key
			oneOf(transport).sendKey(bobPubKey.getEncoded());

			// Bob computes shared secret
			oneOf(crypto).deriveSharedSecret(SHARED_SECRET_LABEL, alicePubKey,
					ourKeyPair, new byte[] {PROTOCOL_VERSION},
					alicePubKey.getEncoded(), bobPubKey.getEncoded());
			will(returnValue(sharedSecret));

			// Bob receives Alices's confirmation record
			oneOf(transport).receiveConfirm();
			will(returnValue(aliceConfirm));

			// Bob verifies Alice's confirmation record
			oneOf(keyAgreementCrypto).deriveConfirmationRecord(sharedSecret,
					alicePayload, bobPayload, alicePubKey, ourKeyPair,
					false, true);
			will(returnValue(aliceConfirm));

			// Bob sends his confirmation record
			oneOf(keyAgreementCrypto).deriveConfirmationRecord(sharedSecret,
					alicePayload, bobPayload, alicePubKey, ourKeyPair,
					false, false);
			will(returnValue(bobConfirm));
			oneOf(transport).sendConfirm(bobConfirm);

			// Bob derives master key
			oneOf(crypto).deriveKey(MASTER_KEY_LABEL, sharedSecret);
			will(returnValue(masterKey));
		}});

		// execute
		assertThat(masterKey, is(equalTo(protocol.perform())));
	}

	@Test(expected = AbortException.class)
	public void testAliceProtocolAbortOnBadKey() throws Exception {
		// set up
		Payload theirPayload = new Payload(bobCommit, emptyList());
		Payload ourPayload = new Payload(aliceCommit, emptyList());
		KeyPair ourKeyPair = new KeyPair(alicePubKey, getAgreementPrivateKey());

		KeyAgreementProtocol protocol = new KeyAgreementProtocol(callbacks,
				crypto, keyAgreementCrypto, payloadEncoder, transport,
				theirPayload, ourPayload, ourKeyPair, true);

		// expectations
		context.checking(new Expectations() {{
			// Helpers
			allowing(crypto).getAgreementKeyParser();
			will(returnValue(keyParser));

			// Alice sends her public key
			oneOf(transport).sendKey(alicePubKey.getEncoded());

			// Alice receives a bad public key
			oneOf(callbacks).connectionWaiting();
			oneOf(transport).receiveKey();
			will(returnValue(badPubKey.getEncoded()));
			oneOf(callbacks).initialRecordReceived();
			oneOf(keyParser).parsePublicKey(badPubKey.getEncoded());
			will(returnValue(badPubKey));

			// Alice verifies Bob's public key
			oneOf(keyAgreementCrypto).deriveKeyCommitment(badPubKey);
			will(returnValue(badCommit));

			// Alice aborts
			oneOf(transport).sendAbort(false);

			// Alice never computes shared secret
			never(crypto).deriveSharedSecret(SHARED_SECRET_LABEL, badPubKey,
					ourKeyPair, new byte[] {PROTOCOL_VERSION},
					alicePubKey.getEncoded(), bobPubKey.getEncoded());
		}});

		// execute
		protocol.perform();
	}

	@Test(expected = AbortException.class)
	public void testBobProtocolAbortOnBadKey() throws Exception {
		// set up
		Payload theirPayload = new Payload(aliceCommit, emptyList());
		Payload ourPayload = new Payload(bobCommit, emptyList());
		KeyPair ourKeyPair = new KeyPair(bobPubKey, getAgreementPrivateKey());

		KeyAgreementProtocol protocol = new KeyAgreementProtocol(callbacks,
				crypto, keyAgreementCrypto, payloadEncoder, transport,
				theirPayload, ourPayload, ourKeyPair, false);

		// expectations
		context.checking(new Expectations() {{
			// Helpers
			allowing(crypto).getAgreementKeyParser();
			will(returnValue(keyParser));

			// Bob receives a bad public key
			oneOf(transport).receiveKey();
			will(returnValue(badPubKey.getEncoded()));
			oneOf(callbacks).initialRecordReceived();
			oneOf(keyParser).parsePublicKey(badPubKey.getEncoded());
			will(returnValue(badPubKey));

			// Bob verifies Alice's public key
			oneOf(keyAgreementCrypto).deriveKeyCommitment(badPubKey);
			will(returnValue(badCommit));

			// Bob aborts
			oneOf(transport).sendAbort(false);

			// Bob never sends his public key
			never(transport).sendKey(bobPubKey.getEncoded());
		}});

		// execute
		protocol.perform();
	}

	@Test(expected = AbortException.class)
	public void testAliceProtocolAbortOnBadConfirm() throws Exception {
		// set up
		Payload theirPayload = new Payload(bobCommit, emptyList());
		Payload ourPayload = new Payload(aliceCommit, emptyList());
		KeyPair ourKeyPair = new KeyPair(alicePubKey, getAgreementPrivateKey());
		SecretKey sharedSecret = getSecretKey();

		KeyAgreementProtocol protocol = new KeyAgreementProtocol(callbacks,
				crypto, keyAgreementCrypto, payloadEncoder, transport,
				theirPayload, ourPayload, ourKeyPair, true);

		// expectations
		context.checking(new Expectations() {{
			// Helpers
			allowing(payloadEncoder).encode(ourPayload);
			will(returnValue(alicePayload));
			allowing(payloadEncoder).encode(theirPayload);
			will(returnValue(bobPayload));
			allowing(crypto).getAgreementKeyParser();
			will(returnValue(keyParser));

			// Alice sends her public key
			oneOf(transport).sendKey(alicePubKey.getEncoded());

			// Alice receives Bob's public key
			oneOf(callbacks).connectionWaiting();
			oneOf(transport).receiveKey();
			will(returnValue(bobPubKey.getEncoded()));
			oneOf(callbacks).initialRecordReceived();
			oneOf(keyParser).parsePublicKey(bobPubKey.getEncoded());
			will(returnValue(bobPubKey));

			// Alice verifies Bob's public key
			oneOf(keyAgreementCrypto).deriveKeyCommitment(bobPubKey);
			will(returnValue(bobCommit));

			// Alice computes shared secret
			oneOf(crypto).deriveSharedSecret(SHARED_SECRET_LABEL, bobPubKey,
					ourKeyPair, new byte[] {PROTOCOL_VERSION},
					alicePubKey.getEncoded(), bobPubKey.getEncoded());
			will(returnValue(sharedSecret));

			// Alice sends her confirmation record
			oneOf(keyAgreementCrypto).deriveConfirmationRecord(sharedSecret,
					bobPayload, alicePayload, bobPubKey, ourKeyPair,
					true, true);
			will(returnValue(aliceConfirm));
			oneOf(transport).sendConfirm(aliceConfirm);

			// Alice receives a bad confirmation record
			oneOf(transport).receiveConfirm();
			will(returnValue(badConfirm));

			// Alice verifies Bob's confirmation record
			oneOf(keyAgreementCrypto).deriveConfirmationRecord(sharedSecret,
					bobPayload, alicePayload, bobPubKey, ourKeyPair,
					true, false);
			will(returnValue(bobConfirm));

			// Alice aborts
			oneOf(transport).sendAbort(false);

			// Alice never derives master key
			never(crypto).deriveKey(MASTER_KEY_LABEL, sharedSecret);
		}});

		// execute
		protocol.perform();
	}

	@Test(expected = AbortException.class)
	public void testBobProtocolAbortOnBadConfirm() throws Exception {
		// set up
		Payload theirPayload = new Payload(aliceCommit, emptyList());
		Payload ourPayload = new Payload(bobCommit, emptyList());
		KeyPair ourKeyPair = new KeyPair(bobPubKey, getAgreementPrivateKey());
		SecretKey sharedSecret = getSecretKey();

		KeyAgreementProtocol protocol = new KeyAgreementProtocol(callbacks,
				crypto, keyAgreementCrypto, payloadEncoder, transport,
				theirPayload, ourPayload, ourKeyPair, false);

		// expectations
		context.checking(new Expectations() {{
			// Helpers
			allowing(payloadEncoder).encode(ourPayload);
			will(returnValue(bobPayload));
			allowing(payloadEncoder).encode(theirPayload);
			will(returnValue(alicePayload));
			allowing(crypto).getAgreementKeyParser();
			will(returnValue(keyParser));

			// Bob receives Alice's public key
			oneOf(transport).receiveKey();
			will(returnValue(alicePubKey.getEncoded()));
			oneOf(callbacks).initialRecordReceived();
			oneOf(keyParser).parsePublicKey(alicePubKey.getEncoded());
			will(returnValue(alicePubKey));

			// Bob verifies Alice's public key
			oneOf(keyAgreementCrypto).deriveKeyCommitment(alicePubKey);
			will(returnValue(aliceCommit));

			// Bob sends his public key
			oneOf(transport).sendKey(bobPubKey.getEncoded());

			// Bob computes shared secret
			oneOf(crypto).deriveSharedSecret(SHARED_SECRET_LABEL, alicePubKey,
					ourKeyPair, new byte[] {PROTOCOL_VERSION},
					alicePubKey.getEncoded(), bobPubKey.getEncoded());
			will(returnValue(sharedSecret));

			// Bob receives a bad confirmation record
			oneOf(transport).receiveConfirm();
			will(returnValue(badConfirm));

			// Bob verifies Alice's confirmation record
			oneOf(keyAgreementCrypto).deriveConfirmationRecord(sharedSecret,
					alicePayload, bobPayload, alicePubKey, ourKeyPair,
					false, true);
			will(returnValue(aliceConfirm));

			// Bob aborts
			oneOf(transport).sendAbort(false);

			// Bob never sends his confirmation record
			never(keyAgreementCrypto).deriveConfirmationRecord(sharedSecret,
					alicePayload, bobPayload, alicePubKey, ourKeyPair,
					false, false);
		}});

		// execute
		protocol.perform();
	}
}