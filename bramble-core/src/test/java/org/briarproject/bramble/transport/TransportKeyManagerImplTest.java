package org.briarproject.bramble.transport;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.crypto.TransportCrypto;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.TaskScheduler;
import org.briarproject.bramble.api.transport.IncomingKeys;
import org.briarproject.bramble.api.transport.KeySetId;
import org.briarproject.bramble.api.transport.OutgoingKeys;
import org.briarproject.bramble.api.transport.StreamContext;
import org.briarproject.bramble.api.transport.TransportKeySet;
import org.briarproject.bramble.api.transport.TransportKeys;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.DbExpectations;
import org.briarproject.bramble.test.RunAction;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.briarproject.bramble.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;
import static org.briarproject.bramble.api.transport.TransportConstants.PROTOCOL_VERSION;
import static org.briarproject.bramble.api.transport.TransportConstants.REORDERING_WINDOW_SIZE;
import static org.briarproject.bramble.api.transport.TransportConstants.TAG_LENGTH;
import static org.briarproject.bramble.test.TestUtils.getContactId;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.test.TestUtils.getTransportId;
import static org.briarproject.bramble.util.ByteUtils.MAX_32_BIT_UNSIGNED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TransportKeyManagerImplTest extends BrambleMockTestCase {

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final TransportCrypto transportCrypto =
			context.mock(TransportCrypto.class);
	private final Executor dbExecutor = context.mock(Executor.class);
	private final TaskScheduler scheduler = context.mock(TaskScheduler.class);
	private final Clock clock = context.mock(Clock.class);

	private final TransportId transportId = getTransportId();
	private final long maxLatency = 30 * 1000; // 30 seconds
	private final long timePeriodLength = maxLatency + MAX_CLOCK_DIFFERENCE;
	private final ContactId contactId = getContactId();
	private final ContactId contactId1 = getContactId();
	private final PendingContactId pendingContactId =
			new PendingContactId(getRandomId());
	private final KeySetId keySetId = new KeySetId(345);
	private final KeySetId keySetId1 = new KeySetId(456);
	private final SecretKey tagKey = getSecretKey();
	private final SecretKey headerKey = getSecretKey();
	private final SecretKey rootKey = getSecretKey();
	private final Random random = new Random();

	private TransportKeyManager transportKeyManager;

	@Before
	public void setUp() {
		transportKeyManager = new TransportKeyManagerImpl(db, transportCrypto,
				dbExecutor, scheduler, clock, transportId, maxLatency);
	}

	@Test
	public void testKeysAreUpdatedAtStartup() throws Exception {
		boolean active = random.nextBoolean();
		TransportKeys shouldUpdate = createTransportKeys(900, 0, active);
		TransportKeys shouldNotUpdate = createTransportKeys(1000, 0, active);
		Collection<TransportKeySet> loaded = asList(
				new TransportKeySet(keySetId, contactId, null, shouldUpdate),
				new TransportKeySet(keySetId1, contactId1, null,
						shouldNotUpdate)
		);
		TransportKeys updated = createTransportKeys(1000, 0, active);
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			// Get the current time (1 ms after start of time period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(timePeriodLength * 1000 + 1));
			// Load the transport keys
			oneOf(db).getTransportKeys(txn, transportId);
			will(returnValue(loaded));
			// Update the transport keys
			oneOf(transportCrypto).updateTransportKeys(shouldUpdate, 1000);
			will(returnValue(updated));
			oneOf(transportCrypto).updateTransportKeys(shouldNotUpdate, 1000);
			will(returnValue(shouldNotUpdate));
			// Encode the tags (3 sets per contact)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(6).of(transportCrypto).encodeTag(
						with(any(byte[].class)), with(tagKey),
						with(PROTOCOL_VERSION), with(i));
				will(new EncodeTagAction());
			}
			// Save the keys that were updated
			oneOf(db).updateTransportKeys(txn, singletonList(
					new TransportKeySet(keySetId, contactId, null, updated)));
			// Schedule a key update at the start of the next time period
			oneOf(scheduler).schedule(with(any(Runnable.class)),
					with(dbExecutor), with(timePeriodLength - 1),
					with(MILLISECONDS));
		}});

		transportKeyManager.start(txn);
		assertEquals(active,
				transportKeyManager.canSendOutgoingStreams(contactId));
	}

	@Test
	public void testRotationKeysForContactAreDerivedAndUpdatedWhenAdded()
			throws Exception {
		boolean alice = random.nextBoolean();
		boolean active = random.nextBoolean();
		TransportKeys transportKeys = createTransportKeys(999, 0, active);
		TransportKeys updated = createTransportKeys(1000, 0, active);
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(transportCrypto).deriveRotationKeys(transportId, rootKey,
					999, alice, active);
			will(returnValue(transportKeys));
			// Get the current time (1 ms after start of time period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(timePeriodLength * 1000 + 1));
			// Update the transport keys
			oneOf(transportCrypto).updateTransportKeys(transportKeys, 1000);
			will(returnValue(updated));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(transportCrypto).encodeTag(
						with(any(byte[].class)), with(tagKey),
						with(PROTOCOL_VERSION), with(i));
				will(new EncodeTagAction());
			}
			// Save the keys
			oneOf(db).addTransportKeys(txn, contactId, updated);
			will(returnValue(keySetId));
		}});

		TransportKeyManager transportKeyManager = new TransportKeyManagerImpl(
				db, transportCrypto, dbExecutor, scheduler, clock, transportId,
				maxLatency);
		// The timestamp is 1 ms before the start of time period 1000
		long timestamp = timePeriodLength * 1000 - 1;
		assertEquals(keySetId, transportKeyManager.addRotationKeys(txn,
				contactId, rootKey, timestamp, alice, active));
		assertEquals(active,
				transportKeyManager.canSendOutgoingStreams(contactId));
	}

	@Test
	public void testHandshakeKeysForContactAreDerivedWhenAdded()
			throws Exception {
		boolean alice = random.nextBoolean();
		TransportKeys transportKeys = createHandshakeKeys(1000, 0, alice);
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			// Get the current time (1 ms after start of time period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(timePeriodLength * 1000 + 1));
			// Derive the transport keys
			oneOf(transportCrypto).deriveHandshakeKeys(transportId, rootKey,
					1000, alice);
			will(returnValue(transportKeys));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(transportCrypto).encodeTag(
						with(any(byte[].class)), with(tagKey),
						with(PROTOCOL_VERSION), with(i));
				will(new EncodeTagAction());
			}
			// Save the keys
			oneOf(db).addTransportKeys(txn, contactId, transportKeys);
			will(returnValue(keySetId));
		}});

		assertEquals(keySetId, transportKeyManager.addHandshakeKeys(txn,
				contactId, rootKey, alice));
		assertTrue(transportKeyManager.canSendOutgoingStreams(contactId));
	}

	@Test
	public void testHandshakeKeysForPendingContactAreDerivedWhenAdded()
			throws Exception {
		boolean alice = random.nextBoolean();
		TransportKeys transportKeys = createHandshakeKeys(1000, 0, alice);
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			// Get the current time (1 ms after start of time period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(timePeriodLength * 1000 + 1));
			// Derive the transport keys
			oneOf(transportCrypto).deriveHandshakeKeys(transportId, rootKey,
					1000, alice);
			will(returnValue(transportKeys));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(transportCrypto).encodeTag(
						with(any(byte[].class)), with(tagKey),
						with(PROTOCOL_VERSION), with(i));
				will(new EncodeTagAction());
			}
			// Save the keys
			oneOf(db).addTransportKeys(txn, pendingContactId, transportKeys);
			will(returnValue(keySetId));
		}});

		assertEquals(keySetId, transportKeyManager.addHandshakeKeys(txn,
				pendingContactId, rootKey, alice));
		assertTrue(transportKeyManager.canSendOutgoingStreams(
				pendingContactId));
	}

	@Test
	public void testOutgoingStreamContextIsNullIfContactIsNotFound()
			throws Exception {
		Transaction txn = new Transaction(null, false);

		TransportKeyManager transportKeyManager = new TransportKeyManagerImpl(
				db, transportCrypto, dbExecutor, scheduler, clock, transportId,
				maxLatency);
		assertNull(transportKeyManager.getStreamContext(txn, contactId));
		assertFalse(transportKeyManager.canSendOutgoingStreams(contactId));
	}

	@Test
	public void testOutgoingStreamContextIsNullIfPendingContactIsNotFound()
			throws Exception {
		Transaction txn = new Transaction(null, false);

		TransportKeyManager transportKeyManager = new TransportKeyManagerImpl(
				db, transportCrypto, dbExecutor, scheduler, clock, transportId,
				maxLatency);
		assertNull(transportKeyManager.getStreamContext(txn, pendingContactId));
		assertFalse(transportKeyManager.canSendOutgoingStreams(
				pendingContactId));
	}

	@Test
	public void testOutgoingStreamContextIsNullIfStreamCounterIsExhausted()
			throws Exception {
		boolean alice = random.nextBoolean();
		// The stream counter has been exhausted
		TransportKeys transportKeys = createTransportKeys(1000,
				MAX_32_BIT_UNSIGNED + 1, true);
		Transaction txn = new Transaction(null, false);

		expectAddContactKeysNotUpdated(alice, true, transportKeys, txn);

		// The timestamp is at the start of time period 1000
		long timestamp = timePeriodLength * 1000;
		assertEquals(keySetId, transportKeyManager.addRotationKeys(
				txn, contactId, rootKey, timestamp, alice, true));
		assertFalse(transportKeyManager.canSendOutgoingStreams(contactId));
		assertNull(transportKeyManager.getStreamContext(txn, contactId));
	}

	@Test
	public void testOutgoingStreamCounterIsIncremented() throws Exception {
		boolean alice = random.nextBoolean();
		// The stream counter can be used one more time before being exhausted
		TransportKeys transportKeys = createTransportKeys(1000,
				MAX_32_BIT_UNSIGNED, true);
		Transaction txn = new Transaction(null, false);

		expectAddContactKeysNotUpdated(alice, true, transportKeys, txn);

		context.checking(new Expectations() {{
			// Increment the stream counter
			oneOf(db).incrementStreamCounter(txn, transportId, keySetId);
		}});

		// The timestamp is at the start of time period 1000
		long timestamp = timePeriodLength * 1000;
		assertEquals(keySetId, transportKeyManager.addRotationKeys(
				txn, contactId, rootKey, timestamp, alice, true));
		// The first request should return a stream context
		assertTrue(transportKeyManager.canSendOutgoingStreams(contactId));
		StreamContext ctx = transportKeyManager.getStreamContext(txn,
				contactId);
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(transportId, ctx.getTransportId());
		assertEquals(tagKey, ctx.getTagKey());
		assertEquals(headerKey, ctx.getHeaderKey());
		assertEquals(MAX_32_BIT_UNSIGNED, ctx.getStreamNumber());
		// The second request should return null, the counter is exhausted
		assertFalse(transportKeyManager.canSendOutgoingStreams(contactId));
		assertNull(transportKeyManager.getStreamContext(txn, contactId));
	}

	@Test
	public void testIncomingStreamContextIsNullIfTagIsNotFound()
			throws Exception {
		boolean alice = random.nextBoolean();
		boolean active = random.nextBoolean();
		TransportKeys transportKeys = createTransportKeys(1000, 0, active);
		Transaction txn = new Transaction(null, false);

		expectAddContactKeysNotUpdated(alice, active, transportKeys, txn);

		// The timestamp is at the start of time period 1000
		long timestamp = timePeriodLength * 1000;
		assertEquals(keySetId, transportKeyManager.addRotationKeys(
				txn, contactId, rootKey, timestamp, alice, active));
		assertEquals(active,
				transportKeyManager.canSendOutgoingStreams(contactId));
		// The tag should not be recognised
		assertNull(transportKeyManager.getStreamContext(txn,
				new byte[TAG_LENGTH]));
	}

	@Test
	public void testTagIsNotRecognisedTwice() throws Exception {
		boolean alice = random.nextBoolean();
		TransportKeys transportKeys = createTransportKeys(1000, 0, true);
		Transaction txn = new Transaction(null, false);

		// Keep a copy of the tags
		List<byte[]> tags = new ArrayList<>();

		context.checking(new Expectations() {{
			oneOf(transportCrypto).deriveRotationKeys(transportId, rootKey,
					1000, alice, true);
			will(returnValue(transportKeys));
			// Get the current time (the start of time period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(timePeriodLength * 1000));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(transportCrypto).encodeTag(
						with(any(byte[].class)), with(tagKey),
						with(PROTOCOL_VERSION), with(i));
				will(new EncodeTagAction(tags));
			}
			// Updated the transport keys (the keys are unaffected)
			oneOf(transportCrypto).updateTransportKeys(transportKeys, 1000);
			will(returnValue(transportKeys));
			// Save the keys
			oneOf(db).addTransportKeys(txn, contactId, transportKeys);
			will(returnValue(keySetId));
			// Encode a new tag after sliding the window
			oneOf(transportCrypto).encodeTag(with(any(byte[].class)),
					with(tagKey), with(PROTOCOL_VERSION),
					with((long) REORDERING_WINDOW_SIZE));
			will(new EncodeTagAction(tags));
			// Save the reordering window (previous time period, base 1)
			oneOf(db).setReorderingWindow(txn, keySetId, transportId, 999,
					1, new byte[REORDERING_WINDOW_SIZE / 8]);
		}});

		// The timestamp is at the start of time period 1000
		long timestamp = timePeriodLength * 1000;
		assertEquals(keySetId, transportKeyManager.addRotationKeys(
				txn, contactId, rootKey, timestamp, alice, true));
		assertTrue(transportKeyManager.canSendOutgoingStreams(contactId));
		// Use the first tag (previous time period, stream number 0)
		assertEquals(REORDERING_WINDOW_SIZE * 3, tags.size());
		byte[] tag = tags.get(0);
		// The first request should return a stream context
		StreamContext ctx = transportKeyManager.getStreamContext(txn, tag);
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(transportId, ctx.getTransportId());
		assertEquals(tagKey, ctx.getTagKey());
		assertEquals(headerKey, ctx.getHeaderKey());
		assertEquals(0L, ctx.getStreamNumber());
		// Another tag should have been encoded
		assertEquals(REORDERING_WINDOW_SIZE * 3 + 1, tags.size());
		// The second request should return null, the tag has already been used
		assertNull(transportKeyManager.getStreamContext(txn, tag));
	}

	@Test
	public void testGetStreamContextOnlyAndMarkTag() throws Exception {
		boolean alice = random.nextBoolean();
		TransportKeys transportKeys = createTransportKeys(1000, 0, true);
		Transaction txn = new Transaction(null, false);

		// Keep a copy of the tags
		List<byte[]> tags = new ArrayList<>();

		context.checking(new Expectations() {{
			oneOf(transportCrypto).deriveRotationKeys(transportId, rootKey,
					1000, alice, true);
			will(returnValue(transportKeys));
			// Get the current time (the start of time period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(timePeriodLength * 1000));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(transportCrypto).encodeTag(
						with(any(byte[].class)), with(tagKey),
						with(PROTOCOL_VERSION), with(i));
				will(new EncodeTagAction(tags));
			}
			// Updated the transport keys (the keys are unaffected)
			oneOf(transportCrypto).updateTransportKeys(transportKeys, 1000);
			will(returnValue(transportKeys));
			// Save the keys
			oneOf(db).addTransportKeys(txn, contactId, transportKeys);
			will(returnValue(keySetId));
			// Encode a new tag after sliding the window
			oneOf(transportCrypto).encodeTag(with(any(byte[].class)),
					with(tagKey), with(PROTOCOL_VERSION),
					with((long) REORDERING_WINDOW_SIZE));
			will(new EncodeTagAction(tags));
			// Save the reordering window (previous time period, base 1)
			oneOf(db).setReorderingWindow(txn, keySetId, transportId, 999,
					1, new byte[REORDERING_WINDOW_SIZE / 8]);
		}});

		// The timestamp is at the start of time period 1000
		long timestamp = timePeriodLength * 1000;
		assertEquals(keySetId, transportKeyManager.addRotationKeys(
				txn, contactId, rootKey, timestamp, alice, true));
		assertTrue(transportKeyManager.canSendOutgoingStreams(contactId));
		// Use the first tag (previous time period, stream number 0)
		assertEquals(REORDERING_WINDOW_SIZE * 3, tags.size());
		byte[] tag = tags.get(0);
		// Repeated request should return same stream context
		StreamContext ctx = transportKeyManager.getStreamContextOnly(txn, tag);
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(transportId, ctx.getTransportId());
		assertEquals(tagKey, ctx.getTagKey());
		assertEquals(headerKey, ctx.getHeaderKey());
		assertEquals(0L, ctx.getStreamNumber());
		ctx = transportKeyManager.getStreamContextOnly(txn, tag);
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(transportId, ctx.getTransportId());
		assertEquals(tagKey, ctx.getTagKey());
		assertEquals(headerKey, ctx.getHeaderKey());
		assertEquals(0L, ctx.getStreamNumber());
		// Then mark tag as recognised
		transportKeyManager.markTagAsRecognised(txn, tag);
		// Another tag should have been encoded
		assertEquals(REORDERING_WINDOW_SIZE * 3 + 1, tags.size());
		// Finally ensure the used tag is not recognised again
		assertNull(transportKeyManager.getStreamContextOnly(txn, tag));
	}

	@Test
	public void testKeysAreUpdatedToCurrentPeriod() throws Exception {
		TransportKeys transportKeys = createTransportKeys(1000, 0, true);
		Collection<TransportKeySet> loaded = singletonList(
				new TransportKeySet(keySetId, contactId, null, transportKeys));
		TransportKeys updated = createTransportKeys(1001, 0, true);
		Transaction txn = new Transaction(null, false);
		Transaction txn1 = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			// Get the current time (the start of time period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(timePeriodLength * 1000));
			// Load the transport keys
			oneOf(db).getTransportKeys(txn, transportId);
			will(returnValue(loaded));
			// Update the transport keys (the keys are unaffected)
			oneOf(transportCrypto).updateTransportKeys(transportKeys, 1000);
			will(returnValue(transportKeys));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(transportCrypto).encodeTag(
						with(any(byte[].class)), with(tagKey),
						with(PROTOCOL_VERSION), with(i));
				will(new EncodeTagAction());
			}
			// Schedule a key update at the start of the next time period
			oneOf(scheduler).schedule(with(any(Runnable.class)),
					with(dbExecutor), with(timePeriodLength),
					with(MILLISECONDS));
			will(new RunAction());
			// Start a transaction for updating keys
			oneOf(db).transaction(with(false), withDbRunnable(txn1));
			// Get the current time (the start of time period 1001)
			oneOf(clock).currentTimeMillis();
			will(returnValue(timePeriodLength * 1001));
			// Update the transport keys
			oneOf(transportCrypto).updateTransportKeys(
					with(any(TransportKeys.class)), with(1001L));
			will(returnValue(updated));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(transportCrypto).encodeTag(
						with(any(byte[].class)), with(tagKey),
						with(PROTOCOL_VERSION), with(i));
				will(new EncodeTagAction());
			}
			// Save the keys that were updated
			oneOf(db).updateTransportKeys(txn1, singletonList(
					new TransportKeySet(keySetId, contactId, null, updated)));
			// Schedule a key update at the start of the next time period
			oneOf(scheduler).schedule(with(any(Runnable.class)),
					with(dbExecutor), with(timePeriodLength),
					with(MILLISECONDS));
		}});

		transportKeyManager.start(txn);
		assertTrue(transportKeyManager.canSendOutgoingStreams(contactId));
	}

	@Test
	public void testActivatingKeys() throws Exception {
		boolean alice = random.nextBoolean();
		TransportKeys transportKeys = createTransportKeys(1000, 0, false);
		Transaction txn = new Transaction(null, false);

		expectAddContactKeysNotUpdated(alice, false, transportKeys, txn);

		context.checking(new Expectations() {{
			// Activate the keys
			oneOf(db).setTransportKeysActive(txn, transportId, keySetId);
			// Increment the stream counter
			oneOf(db).incrementStreamCounter(txn, transportId, keySetId);
		}});

		// The timestamp is at the start of time period 1000
		long timestamp = timePeriodLength * 1000;
		assertEquals(keySetId, transportKeyManager.addRotationKeys(
				txn, contactId, rootKey, timestamp, alice, false));
		// The keys are inactive so no stream context should be returned
		assertFalse(transportKeyManager.canSendOutgoingStreams(contactId));
		assertNull(transportKeyManager.getStreamContext(txn, contactId));
		transportKeyManager.activateKeys(txn, keySetId);
		// The keys are active so a stream context should be returned
		assertTrue(transportKeyManager.canSendOutgoingStreams(contactId));
		StreamContext ctx = transportKeyManager.getStreamContext(txn,
				contactId);
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(transportId, ctx.getTransportId());
		assertEquals(tagKey, ctx.getTagKey());
		assertEquals(headerKey, ctx.getHeaderKey());
		assertEquals(0L, ctx.getStreamNumber());
	}

	@Test
	public void testRecognisingTagActivatesOutgoingKeys() throws Exception {
		boolean alice = random.nextBoolean();
		TransportKeys transportKeys = createTransportKeys(1000, 0, false);
		Transaction txn = new Transaction(null, false);

		// Keep a copy of the tags
		List<byte[]> tags = new ArrayList<>();

		context.checking(new Expectations() {{
			oneOf(transportCrypto).deriveRotationKeys(transportId, rootKey,
					1000, alice, false);
			will(returnValue(transportKeys));
			// Get the current time (the start of time period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(timePeriodLength * 1000));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(transportCrypto).encodeTag(
						with(any(byte[].class)), with(tagKey),
						with(PROTOCOL_VERSION), with(i));
				will(new EncodeTagAction(tags));
			}
			// Update the transport keys (the keys are unaffected)
			oneOf(transportCrypto).updateTransportKeys(transportKeys, 1000);
			will(returnValue(transportKeys));
			// Save the keys
			oneOf(db).addTransportKeys(txn, contactId, transportKeys);
			will(returnValue(keySetId));
			// Encode a new tag after sliding the window
			oneOf(transportCrypto).encodeTag(with(any(byte[].class)),
					with(tagKey), with(PROTOCOL_VERSION),
					with((long) REORDERING_WINDOW_SIZE));
			will(new EncodeTagAction(tags));
			// Save the reordering window (previous time period, base 1)
			oneOf(db).setReorderingWindow(txn, keySetId, transportId, 999,
					1, new byte[REORDERING_WINDOW_SIZE / 8]);
			// Activate the keys
			oneOf(db).setTransportKeysActive(txn, transportId, keySetId);
			// Increment the stream counter
			oneOf(db).incrementStreamCounter(txn, transportId, keySetId);
		}});

		// The timestamp is at the start of time period 1000
		long timestamp = timePeriodLength * 1000;
		assertEquals(keySetId, transportKeyManager.addRotationKeys(
				txn, contactId, rootKey, timestamp, alice, false));
		// The keys are inactive so no stream context should be returned
		assertFalse(transportKeyManager.canSendOutgoingStreams(contactId));
		assertNull(transportKeyManager.getStreamContext(txn, contactId));
		// Recognising an incoming tag should activate the outgoing keys
		assertEquals(REORDERING_WINDOW_SIZE * 3, tags.size());
		byte[] tag = tags.get(0);
		StreamContext ctx = transportKeyManager.getStreamContext(txn, tag);
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(transportId, ctx.getTransportId());
		assertEquals(tagKey, ctx.getTagKey());
		assertEquals(headerKey, ctx.getHeaderKey());
		assertEquals(0L, ctx.getStreamNumber());
		// The keys are active so a stream context should be returned
		assertTrue(transportKeyManager.canSendOutgoingStreams(contactId));
		ctx = transportKeyManager.getStreamContext(txn, contactId);
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(transportId, ctx.getTransportId());
		assertEquals(tagKey, ctx.getTagKey());
		assertEquals(headerKey, ctx.getHeaderKey());
		assertEquals(0L, ctx.getStreamNumber());
	}

	private void expectAddContactKeysNotUpdated(boolean alice, boolean active,
			TransportKeys transportKeys, Transaction txn) throws Exception {
		context.checking(new Expectations() {{
			oneOf(transportCrypto).deriveRotationKeys(transportId, rootKey,
					1000, alice, active);
			will(returnValue(transportKeys));
			// Get the current time (the start of time period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(timePeriodLength * 1000));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(transportCrypto).encodeTag(
						with(any(byte[].class)), with(tagKey),
						with(PROTOCOL_VERSION), with(i));
				will(new EncodeTagAction());
			}
			// Upate the transport keys (the keys are unaffected)
			oneOf(transportCrypto).updateTransportKeys(transportKeys, 1000);
			will(returnValue(transportKeys));
			// Save the keys
			oneOf(db).addTransportKeys(txn, contactId, transportKeys);
			will(returnValue(keySetId));
		}});
	}

	private TransportKeys createTransportKeys(long timePeriod,
			long streamCounter, boolean active) {
		IncomingKeys inPrev = new IncomingKeys(tagKey, headerKey,
				timePeriod - 1);
		IncomingKeys inCurr = new IncomingKeys(tagKey, headerKey,
				timePeriod);
		IncomingKeys inNext = new IncomingKeys(tagKey, headerKey,
				timePeriod + 1);
		OutgoingKeys outCurr = new OutgoingKeys(tagKey, headerKey,
				timePeriod, streamCounter, active);
		return new TransportKeys(transportId, inPrev, inCurr, inNext, outCurr);
	}

	@SuppressWarnings("SameParameterValue")
	private TransportKeys createHandshakeKeys(long timePeriod,
			long streamCounter, boolean alice) {
		IncomingKeys inPrev = new IncomingKeys(tagKey, headerKey,
				timePeriod - 1);
		IncomingKeys inCurr = new IncomingKeys(tagKey, headerKey,
				timePeriod);
		IncomingKeys inNext = new IncomingKeys(tagKey, headerKey,
				timePeriod + 1);
		OutgoingKeys outCurr = new OutgoingKeys(tagKey, headerKey,
				timePeriod, streamCounter, true);
		return new TransportKeys(transportId, inPrev, inCurr, inNext, outCurr,
				rootKey, alice);
	}

	private class EncodeTagAction implements Action {

		private final Collection<byte[]> tags;

		private EncodeTagAction() {
			tags = null;
		}

		private EncodeTagAction(Collection<byte[]> tags) {
			this.tags = tags;
		}

		@Override
		public Object invoke(Invocation invocation) {
			byte[] tag = (byte[]) invocation.getParameter(0);
			random.nextBytes(tag);
			if (tags != null) tags.add(tag);
			return null;
		}

		@Override
		public void describeTo(Description description) {
			description.appendText("encodes a tag");
		}
	}
}
