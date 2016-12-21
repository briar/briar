package org.briarproject.bramble.keyagreement;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.keyagreement.Payload;
import org.briarproject.bramble.api.keyagreement.PayloadEncoder;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestUtils;
import org.jmock.Expectations;
import org.jmock.auto.Mock;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Rule;
import org.junit.Test;

import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.COMMIT_LENGTH;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class KeyAgreementProtocolTest extends BrambleTestCase {

	@Rule
	public JUnitRuleMockery context = new JUnitRuleMockery() {{
		// So we can mock concrete classes like KeyAgreementTransport
		setImposteriser(ClassImposteriser.INSTANCE);
	}};

	private static final byte[] ALICE_PUBKEY = TestUtils.getRandomBytes(32);
	private static final byte[] ALICE_COMMIT =
			TestUtils.getRandomBytes(COMMIT_LENGTH);
	private static final byte[] ALICE_PAYLOAD =
			TestUtils.getRandomBytes(COMMIT_LENGTH + 8);

	private static final byte[] BOB_PUBKEY = TestUtils.getRandomBytes(32);
	private static final byte[] BOB_COMMIT =
			TestUtils.getRandomBytes(COMMIT_LENGTH);
	private static final byte[] BOB_PAYLOAD =
			TestUtils.getRandomBytes(COMMIT_LENGTH + 19);

	private static final byte[] ALICE_CONFIRM =
			TestUtils.getRandomBytes(SecretKey.LENGTH);
	private static final byte[] BOB_CONFIRM =
			TestUtils.getRandomBytes(SecretKey.LENGTH);

	private static final byte[] BAD_PUBKEY = TestUtils.getRandomBytes(32);
	private static final byte[] BAD_COMMIT =
			TestUtils.getRandomBytes(COMMIT_LENGTH);
	private static final byte[] BAD_CONFIRM =
			TestUtils.getRandomBytes(SecretKey.LENGTH);

	@Mock
	KeyAgreementProtocol.Callbacks callbacks;
	@Mock
	CryptoComponent crypto;
	@Mock
	PayloadEncoder payloadEncoder;
	@Mock
	KeyAgreementTransport transport;
	@Mock
	PublicKey ourPubKey;

	@Test
	public void testAliceProtocol() throws Exception {
		// set up
		final Payload theirPayload = new Payload(BOB_COMMIT, null);
		final Payload ourPayload = new Payload(ALICE_COMMIT, null);
		final KeyPair ourKeyPair = new KeyPair(ourPubKey, null);
		final SecretKey sharedSecret = TestUtils.getSecretKey();
		final SecretKey masterSecret = TestUtils.getSecretKey();

		KeyAgreementProtocol protocol =
				new KeyAgreementProtocol(callbacks, crypto, payloadEncoder,
						transport, theirPayload, ourPayload, ourKeyPair, true);

		// expectations
		context.checking(new Expectations() {{
			// Helpers
			allowing(payloadEncoder).encode(ourPayload);
			will(returnValue(ALICE_PAYLOAD));
			allowing(payloadEncoder).encode(theirPayload);
			will(returnValue(BOB_PAYLOAD));
			allowing(ourPubKey).getEncoded();
			will(returnValue(ALICE_PUBKEY));

			// Alice sends her public key
			oneOf(transport).sendKey(ALICE_PUBKEY);

			// Alice receives Bob's public key
			oneOf(callbacks).connectionWaiting();
			oneOf(transport).receiveKey();
			will(returnValue(BOB_PUBKEY));
			oneOf(callbacks).initialRecordReceived();

			// Alice verifies Bob's public key
			oneOf(crypto).deriveKeyCommitment(BOB_PUBKEY);
			will(returnValue(BOB_COMMIT));

			// Alice computes shared secret
			oneOf(crypto).deriveSharedSecret(BOB_PUBKEY, ourKeyPair, true);
			will(returnValue(sharedSecret));

			// Alice sends her confirmation record
			oneOf(crypto).deriveConfirmationRecord(sharedSecret, BOB_PAYLOAD,
					ALICE_PAYLOAD, BOB_PUBKEY, ourKeyPair, true, true);
			will(returnValue(ALICE_CONFIRM));
			oneOf(transport).sendConfirm(ALICE_CONFIRM);

			// Alice receives Bob's confirmation record
			oneOf(transport).receiveConfirm();
			will(returnValue(BOB_CONFIRM));

			// Alice verifies Bob's confirmation record
			oneOf(crypto).deriveConfirmationRecord(sharedSecret, BOB_PAYLOAD,
					ALICE_PAYLOAD, BOB_PUBKEY, ourKeyPair, true, false);
			will(returnValue(BOB_CONFIRM));

			// Alice computes master secret
			oneOf(crypto).deriveMasterSecret(sharedSecret);
			will(returnValue(masterSecret));
		}});

		// execute
		assertThat(masterSecret, is(equalTo(protocol.perform())));
	}

	@Test
	public void testBobProtocol() throws Exception {
		// set up
		final Payload theirPayload = new Payload(ALICE_COMMIT, null);
		final Payload ourPayload = new Payload(BOB_COMMIT, null);
		final KeyPair ourKeyPair = new KeyPair(ourPubKey, null);
		final SecretKey sharedSecret = TestUtils.getSecretKey();
		final SecretKey masterSecret = TestUtils.getSecretKey();

		KeyAgreementProtocol protocol =
				new KeyAgreementProtocol(callbacks, crypto, payloadEncoder,
						transport, theirPayload, ourPayload, ourKeyPair, false);

		// expectations
		context.checking(new Expectations() {{
			// Helpers
			allowing(payloadEncoder).encode(ourPayload);
			will(returnValue(BOB_PAYLOAD));
			allowing(payloadEncoder).encode(theirPayload);
			will(returnValue(ALICE_PAYLOAD));
			allowing(ourPubKey).getEncoded();
			will(returnValue(BOB_PUBKEY));

			// Bob receives Alice's public key
			oneOf(transport).receiveKey();
			will(returnValue(ALICE_PUBKEY));
			oneOf(callbacks).initialRecordReceived();

			// Bob verifies Alice's public key
			oneOf(crypto).deriveKeyCommitment(ALICE_PUBKEY);
			will(returnValue(ALICE_COMMIT));

			// Bob sends his public key
			oneOf(transport).sendKey(BOB_PUBKEY);

			// Bob computes shared secret
			oneOf(crypto).deriveSharedSecret(ALICE_PUBKEY, ourKeyPair, false);
			will(returnValue(sharedSecret));

			// Bob receives Alices's confirmation record
			oneOf(transport).receiveConfirm();
			will(returnValue(ALICE_CONFIRM));

			// Bob verifies Alice's confirmation record
			oneOf(crypto).deriveConfirmationRecord(sharedSecret, ALICE_PAYLOAD,
					BOB_PAYLOAD, ALICE_PUBKEY, ourKeyPair, false, true);
			will(returnValue(ALICE_CONFIRM));

			// Bob sends his confirmation record
			oneOf(crypto).deriveConfirmationRecord(sharedSecret, ALICE_PAYLOAD,
					BOB_PAYLOAD, ALICE_PUBKEY, ourKeyPair, false, false);
			will(returnValue(BOB_CONFIRM));
			oneOf(transport).sendConfirm(BOB_CONFIRM);

			// Bob computes master secret
			oneOf(crypto).deriveMasterSecret(sharedSecret);
			will(returnValue(masterSecret));
		}});

		// execute
		assertThat(masterSecret, is(equalTo(protocol.perform())));
	}

	@Test(expected = AbortException.class)
	public void testAliceProtocolAbortOnBadKey() throws Exception {
		// set up
		final Payload theirPayload = new Payload(BOB_COMMIT, null);
		final Payload ourPayload = new Payload(ALICE_COMMIT, null);
		final KeyPair ourKeyPair = new KeyPair(ourPubKey, null);

		KeyAgreementProtocol protocol =
				new KeyAgreementProtocol(callbacks, crypto, payloadEncoder,
						transport, theirPayload, ourPayload, ourKeyPair, true);

		// expectations
		context.checking(new Expectations() {{
			// Helpers
			allowing(ourPubKey).getEncoded();
			will(returnValue(ALICE_PUBKEY));

			// Alice sends her public key
			oneOf(transport).sendKey(ALICE_PUBKEY);

			// Alice receives a bad public key
			oneOf(callbacks).connectionWaiting();
			oneOf(transport).receiveKey();
			will(returnValue(BAD_PUBKEY));
			oneOf(callbacks).initialRecordReceived();

			// Alice verifies Bob's public key
			oneOf(crypto).deriveKeyCommitment(BAD_PUBKEY);
			will(returnValue(BAD_COMMIT));

			// Alice aborts
			oneOf(transport).sendAbort(false);

			// Alice never computes shared secret
			never(crypto).deriveSharedSecret(BAD_PUBKEY, ourKeyPair, true);
		}});

		// execute
		protocol.perform();
	}

	@Test(expected = AbortException.class)
	public void testBobProtocolAbortOnBadKey() throws Exception {
		// set up
		final Payload theirPayload = new Payload(ALICE_COMMIT, null);
		final Payload ourPayload = new Payload(BOB_COMMIT, null);
		final KeyPair ourKeyPair = new KeyPair(ourPubKey, null);

		KeyAgreementProtocol protocol =
				new KeyAgreementProtocol(callbacks, crypto, payloadEncoder,
						transport, theirPayload, ourPayload, ourKeyPair, false);

		// expectations
		context.checking(new Expectations() {{
			// Helpers
			allowing(ourPubKey).getEncoded();
			will(returnValue(BOB_PUBKEY));

			// Bob receives a bad public key
			oneOf(transport).receiveKey();
			will(returnValue(BAD_PUBKEY));
			oneOf(callbacks).initialRecordReceived();

			// Bob verifies Alice's public key
			oneOf(crypto).deriveKeyCommitment(BAD_PUBKEY);
			will(returnValue(BAD_COMMIT));

			// Bob aborts
			oneOf(transport).sendAbort(false);

			// Bob never sends his public key
			never(transport).sendKey(BOB_PUBKEY);
		}});

		// execute
		protocol.perform();
	}

	@Test(expected = AbortException.class)
	public void testAliceProtocolAbortOnBadConfirm() throws Exception {
		// set up
		final Payload theirPayload = new Payload(BOB_COMMIT, null);
		final Payload ourPayload = new Payload(ALICE_COMMIT, null);
		final KeyPair ourKeyPair = new KeyPair(ourPubKey, null);
		final SecretKey sharedSecret = TestUtils.getSecretKey();

		KeyAgreementProtocol protocol =
				new KeyAgreementProtocol(callbacks, crypto, payloadEncoder,
						transport, theirPayload, ourPayload, ourKeyPair, true);

		// expectations
		context.checking(new Expectations() {{
			// Helpers
			allowing(payloadEncoder).encode(ourPayload);
			will(returnValue(ALICE_PAYLOAD));
			allowing(payloadEncoder).encode(theirPayload);
			will(returnValue(BOB_PAYLOAD));
			allowing(ourPubKey).getEncoded();
			will(returnValue(ALICE_PUBKEY));

			// Alice sends her public key
			oneOf(transport).sendKey(ALICE_PUBKEY);

			// Alice receives Bob's public key
			oneOf(callbacks).connectionWaiting();
			oneOf(transport).receiveKey();
			will(returnValue(BOB_PUBKEY));
			oneOf(callbacks).initialRecordReceived();

			// Alice verifies Bob's public key
			oneOf(crypto).deriveKeyCommitment(BOB_PUBKEY);
			will(returnValue(BOB_COMMIT));

			// Alice computes shared secret
			oneOf(crypto).deriveSharedSecret(BOB_PUBKEY, ourKeyPair, true);
			will(returnValue(sharedSecret));

			// Alice sends her confirmation record
			oneOf(crypto).deriveConfirmationRecord(sharedSecret, BOB_PAYLOAD,
					ALICE_PAYLOAD, BOB_PUBKEY, ourKeyPair, true, true);
			will(returnValue(ALICE_CONFIRM));
			oneOf(transport).sendConfirm(ALICE_CONFIRM);

			// Alice receives a bad confirmation record
			oneOf(transport).receiveConfirm();
			will(returnValue(BAD_CONFIRM));

			// Alice verifies Bob's confirmation record
			oneOf(crypto).deriveConfirmationRecord(sharedSecret, BOB_PAYLOAD,
					ALICE_PAYLOAD, BOB_PUBKEY, ourKeyPair, true, false);
			will(returnValue(BOB_CONFIRM));

			// Alice aborts
			oneOf(transport).sendAbort(false);

			// Alice never computes master secret
			never(crypto).deriveMasterSecret(sharedSecret);
		}});

		// execute
		protocol.perform();
	}

	@Test(expected = AbortException.class)
	public void testBobProtocolAbortOnBadConfirm() throws Exception {
		// set up
		final Payload theirPayload = new Payload(ALICE_COMMIT, null);
		final Payload ourPayload = new Payload(BOB_COMMIT, null);
		final KeyPair ourKeyPair = new KeyPair(ourPubKey, null);
		final SecretKey sharedSecret = TestUtils.getSecretKey();

		KeyAgreementProtocol protocol =
				new KeyAgreementProtocol(callbacks, crypto, payloadEncoder,
						transport, theirPayload, ourPayload, ourKeyPair, false);

		// expectations
		context.checking(new Expectations() {{
			// Helpers
			allowing(payloadEncoder).encode(ourPayload);
			will(returnValue(BOB_PAYLOAD));
			allowing(payloadEncoder).encode(theirPayload);
			will(returnValue(ALICE_PAYLOAD));
			allowing(ourPubKey).getEncoded();
			will(returnValue(BOB_PUBKEY));

			// Bob receives Alice's public key
			oneOf(transport).receiveKey();
			will(returnValue(ALICE_PUBKEY));
			oneOf(callbacks).initialRecordReceived();

			// Bob verifies Alice's public key
			oneOf(crypto).deriveKeyCommitment(ALICE_PUBKEY);
			will(returnValue(ALICE_COMMIT));

			// Bob sends his public key
			oneOf(transport).sendKey(BOB_PUBKEY);

			// Bob computes shared secret
			oneOf(crypto).deriveSharedSecret(ALICE_PUBKEY, ourKeyPair, false);
			will(returnValue(sharedSecret));

			// Bob receives a bad confirmation record
			oneOf(transport).receiveConfirm();
			will(returnValue(BAD_CONFIRM));

			// Bob verifies Alice's confirmation record
			oneOf(crypto).deriveConfirmationRecord(sharedSecret, ALICE_PAYLOAD,
					BOB_PAYLOAD, ALICE_PUBKEY, ourKeyPair, false, true);
			will(returnValue(ALICE_CONFIRM));

			// Bob aborts
			oneOf(transport).sendAbort(false);

			// Bob never sends his confirmation record
			never(crypto).deriveConfirmationRecord(sharedSecret, ALICE_PAYLOAD,
					BOB_PAYLOAD, ALICE_PUBKEY, ourKeyPair, false, false);
		}});

		// execute
		protocol.perform();
	}
}