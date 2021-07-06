package org.briarproject.bramble.transport;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.contact.event.ContactRemovedEvent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.crypto.TransportCrypto;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.plugin.PluginConfig;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory;
import org.briarproject.bramble.api.transport.KeySetId;
import org.briarproject.bramble.api.transport.StreamContext;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.DbExpectations;
import org.jmock.Expectations;
import org.jmock.lib.concurrent.DeterministicExecutor;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Random;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.briarproject.bramble.api.transport.TransportConstants.TAG_LENGTH;
import static org.briarproject.bramble.test.TestUtils.getAgreementPrivateKey;
import static org.briarproject.bramble.test.TestUtils.getAgreementPublicKey;
import static org.briarproject.bramble.test.TestUtils.getContactId;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.test.TestUtils.getTransportId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class KeyManagerImplTest extends BrambleMockTestCase {

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final PluginConfig pluginConfig = context.mock(PluginConfig.class);
	private final TransportKeyManagerFactory transportKeyManagerFactory =
			context.mock(TransportKeyManagerFactory.class);
	private final TransportKeyManager transportKeyManager =
			context.mock(TransportKeyManager.class);
	private final TransportCrypto transportCrypto =
			context.mock(TransportCrypto.class);

	private final DeterministicExecutor executor = new DeterministicExecutor();
	private final Transaction txn = new Transaction(null, false);
	private final ContactId contactId = getContactId();
	private final PendingContactId pendingContactId =
			new PendingContactId(getRandomId());
	private final KeySetId keySetId = new KeySetId(345);
	private final TransportId transportId = getTransportId();
	private final TransportId unknownTransportId = getTransportId();
	private final StreamContext contactStreamContext =
			new StreamContext(contactId, null, transportId, getSecretKey(),
					getSecretKey(), 1, false);
	private final StreamContext pendingContactStreamContext =
			new StreamContext(null, pendingContactId, transportId,
					getSecretKey(), getSecretKey(), 1, true);
	private final byte[] tag = getRandomBytes(TAG_LENGTH);
	private final PublicKey theirPublicKey = getAgreementPublicKey();
	private final KeyPair ourKeyPair =
			new KeyPair(getAgreementPublicKey(), getAgreementPrivateKey());
	private final SecretKey staticMasterKey = getSecretKey();
	private final SecretKey rootKey = getSecretKey();
	private final Random random = new Random();

	private KeyManagerImpl keyManager;

	@Before
	public void testStartService() throws Exception {
		Transaction txn = new Transaction(null, false);
		SimplexPluginFactory pluginFactory =
				context.mock(SimplexPluginFactory.class);
		Collection<SimplexPluginFactory> factories =
				singletonList(pluginFactory);
		long maxLatency = 1337;

		context.checking(new Expectations() {{
			allowing(pluginConfig).getSimplexFactories();
			will(returnValue(factories));
			allowing(pluginFactory).getId();
			will(returnValue(transportId));
			allowing(pluginFactory).getMaxLatency();
			will(returnValue(maxLatency));
			allowing(pluginConfig).getDuplexFactories();
			will(returnValue(emptyList()));
			oneOf(transportKeyManagerFactory)
					.createTransportKeyManager(transportId, maxLatency);
			will(returnValue(transportKeyManager));
		}});

		keyManager = new KeyManagerImpl(db, executor,
				pluginConfig, transportCrypto, transportKeyManagerFactory);

		context.checking(new DbExpectations() {{
			oneOf(db).addTransport(txn, transportId, maxLatency);
			oneOf(db).transaction(with(false), withDbRunnable(txn));
			oneOf(transportKeyManager).start(txn);
		}});

		keyManager.startService();
	}

	@Test
	public void testAddContactWithRotationModeKeys() throws Exception {
		SecretKey secretKey = getSecretKey();
		long timestamp = System.currentTimeMillis();
		boolean alice = random.nextBoolean();
		boolean active = random.nextBoolean();

		context.checking(new Expectations() {{
			oneOf(transportKeyManager).addRotationKeys(txn,
					contactId, secretKey, timestamp, alice, active);
			will(returnValue(keySetId));
		}});

		Map<TransportId, KeySetId> ids = keyManager.addRotationKeys(
				txn, contactId, secretKey, timestamp, alice, active);
		assertEquals(singletonMap(transportId, keySetId), ids);
	}

	@Test
	public void testAddContactWithHandshakePublicKey() throws Exception {
		boolean alice = random.nextBoolean();

		context.checking(new Expectations() {{
			oneOf(transportCrypto)
					.deriveStaticMasterKey(theirPublicKey, ourKeyPair);
			will(returnValue(staticMasterKey));
			oneOf(transportCrypto)
					.deriveHandshakeRootKey(staticMasterKey, false);
			will(returnValue(rootKey));
			oneOf(transportCrypto).isAlice(theirPublicKey, ourKeyPair);
			will(returnValue(alice));
			oneOf(transportKeyManager).addHandshakeKeys(txn, contactId,
					rootKey, alice);
			will(returnValue(keySetId));
		}});

		Map<TransportId, KeySetId> ids = keyManager.addContact(txn, contactId,
				theirPublicKey, ourKeyPair);
		assertEquals(singletonMap(transportId, keySetId), ids);
	}

	@Test
	public void testAddPendingContact() throws Exception {
		boolean alice = random.nextBoolean();

		context.checking(new Expectations() {{
			oneOf(transportCrypto)
					.deriveStaticMasterKey(theirPublicKey, ourKeyPair);
			will(returnValue(staticMasterKey));
			oneOf(transportCrypto)
					.deriveHandshakeRootKey(staticMasterKey, true);
			will(returnValue(rootKey));
			oneOf(transportCrypto).isAlice(theirPublicKey, ourKeyPair);
			will(returnValue(alice));
			oneOf(transportKeyManager).addHandshakeKeys(txn, pendingContactId,
					rootKey, alice);
			will(returnValue(keySetId));
		}});

		Map<TransportId, KeySetId> ids = keyManager.addPendingContact(txn,
				pendingContactId, theirPublicKey, ourKeyPair);
		assertEquals(singletonMap(transportId, keySetId), ids);
	}

	@Test
	public void testGetStreamContextForContactWithUnknownTransport()
			throws Exception {
		assertNull(keyManager.getStreamContext(contactId, unknownTransportId));
	}

	@Test
	public void testGetStreamContextForPendingContactWithUnknownTransport()
			throws Exception {
		assertNull(keyManager.getStreamContext(pendingContactId,
				unknownTransportId));
	}

	@Test
	public void testGetStreamContextForContact() throws Exception {
		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithNullableResult(with(false),
					withNullableDbCallable(txn));
			oneOf(transportKeyManager).getStreamContext(txn, contactId);
			will(returnValue(contactStreamContext));
		}});

		assertEquals(contactStreamContext,
				keyManager.getStreamContext(contactId, transportId));
	}

	@Test
	public void testGetStreamContextForPendingContact() throws Exception {
		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithNullableResult(with(false),
					withNullableDbCallable(txn));
			oneOf(transportKeyManager).getStreamContext(txn, pendingContactId);
			will(returnValue(pendingContactStreamContext));
		}});

		assertEquals(pendingContactStreamContext,
				keyManager.getStreamContext(pendingContactId, transportId));
	}

	@Test
	public void testGetStreamContextForTagAndUnknownTransport()
			throws Exception {
		assertNull(keyManager.getStreamContext(unknownTransportId, tag));
	}

	@Test
	public void testGetStreamContextForTag() throws Exception {
		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithNullableResult(with(false),
					withNullableDbCallable(txn));
			oneOf(transportKeyManager).getStreamContext(txn, tag);
			will(returnValue(contactStreamContext));
		}});

		assertEquals(contactStreamContext,
				keyManager.getStreamContext(transportId, tag));
	}

	@Test
	public void testContactRemovedEvent() {
		ContactRemovedEvent event = new ContactRemovedEvent(contactId);

		context.checking(new Expectations() {{
			oneOf(transportKeyManager).removeContact(contactId);
		}});

		keyManager.eventOccurred(event);
		executor.runUntilIdle();
	}

	@Test
	public void testAddMultipleRotationKeySets() throws Exception {
		long timestamp = System.currentTimeMillis();
		boolean alice = random.nextBoolean();
		boolean active = random.nextBoolean();

		context.checking(new Expectations() {{
			oneOf(transportKeyManager).addRotationKeys(txn, contactId,
					rootKey, timestamp, alice, active);
			will(returnValue(keySetId));
		}});

		assertEquals(singletonMap(transportId, keySetId),
				keyManager.addRotationKeys(txn, contactId, rootKey, timestamp,
						alice, active));
	}

	@Test
	public void testAddSingleRotationKeySet() throws Exception {
		long timestamp = System.currentTimeMillis();
		boolean alice = random.nextBoolean();
		boolean active = random.nextBoolean();

		context.checking(new Expectations() {{
			oneOf(transportKeyManager).addRotationKeys(txn, contactId,
					rootKey, timestamp, alice, active);
			will(returnValue(keySetId));
		}});

		assertEquals(keySetId, keyManager.addRotationKeys(txn, contactId,
				transportId, rootKey, timestamp, alice, active));
	}
}
