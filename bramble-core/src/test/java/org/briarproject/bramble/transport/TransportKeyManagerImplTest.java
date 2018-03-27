package org.briarproject.bramble.transport;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.crypto.TransportCrypto;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.transport.IncomingKeys;
import org.briarproject.bramble.api.transport.KeySet;
import org.briarproject.bramble.api.transport.KeySetId;
import org.briarproject.bramble.api.transport.OutgoingKeys;
import org.briarproject.bramble.api.transport.StreamContext;
import org.briarproject.bramble.api.transport.TransportKeys;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.RunAction;
import org.briarproject.bramble.test.TestUtils;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.briarproject.bramble.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;
import static org.briarproject.bramble.api.transport.TransportConstants.PROTOCOL_VERSION;
import static org.briarproject.bramble.api.transport.TransportConstants.REORDERING_WINDOW_SIZE;
import static org.briarproject.bramble.api.transport.TransportConstants.TAG_LENGTH;
import static org.briarproject.bramble.util.ByteUtils.MAX_32_BIT_UNSIGNED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class TransportKeyManagerImplTest extends BrambleMockTestCase {

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final TransportCrypto transportCrypto =
			context.mock(TransportCrypto.class);
	private final Executor dbExecutor = context.mock(Executor.class);
	private final ScheduledExecutorService scheduler =
			context.mock(ScheduledExecutorService.class);
	private final Clock clock = context.mock(Clock.class);

	private final TransportId transportId = new TransportId("id");
	private final long maxLatency = 30 * 1000; // 30 seconds
	private final long rotationPeriodLength = maxLatency + MAX_CLOCK_DIFFERENCE;
	private final ContactId contactId = new ContactId(123);
	private final ContactId contactId1 = new ContactId(234);
	private final KeySetId keySetId = new KeySetId(345);
	private final KeySetId keySetId1 = new KeySetId(456);
	private final KeySetId keySetId2 = new KeySetId(567);
	private final SecretKey tagKey = TestUtils.getSecretKey();
	private final SecretKey headerKey = TestUtils.getSecretKey();
	private final SecretKey masterKey = TestUtils.getSecretKey();
	private final Random random = new Random();

	@Test
	public void testKeysAreRotatedAtStartup() throws Exception {
		TransportKeys shouldRotate = createTransportKeys(900, 0);
		TransportKeys shouldNotRotate = createTransportKeys(1000, 0);
		TransportKeys shouldRotate1 = createTransportKeys(999, 0);
		Collection<KeySet> loaded = asList(
				new KeySet(keySetId, contactId, shouldRotate),
				new KeySet(keySetId1, contactId1, shouldNotRotate),
				new KeySet(keySetId2, null, shouldRotate1)
		);
		TransportKeys rotated = createTransportKeys(1000, 0);
		TransportKeys rotated1 = createTransportKeys(1000, 0);
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			// Get the current time (1 ms after start of rotation period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(rotationPeriodLength * 1000 + 1));
			// Load the transport keys
			oneOf(db).getTransportKeys(txn, transportId);
			will(returnValue(loaded));
			// Rotate the transport keys
			oneOf(transportCrypto).rotateTransportKeys(shouldRotate, 1000);
			will(returnValue(rotated));
			oneOf(transportCrypto).rotateTransportKeys(shouldNotRotate, 1000);
			will(returnValue(shouldNotRotate));
			oneOf(transportCrypto).rotateTransportKeys(shouldRotate1, 1000);
			will(returnValue(rotated1));
			// Encode the tags (3 sets per contact)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(6).of(transportCrypto).encodeTag(
						with(any(byte[].class)), with(tagKey),
						with(PROTOCOL_VERSION), with(i));
				will(new EncodeTagAction());
			}
			// Save the keys that were rotated
			oneOf(db).updateTransportKeys(txn, asList(
					new KeySet(keySetId, contactId, rotated),
					new KeySet(keySetId2, null, rotated1))
			);
			// Schedule key rotation at the start of the next rotation period
			oneOf(scheduler).schedule(with(any(Runnable.class)),
					with(rotationPeriodLength - 1), with(MILLISECONDS));
		}});

		TransportKeyManager transportKeyManager = new TransportKeyManagerImpl(
				db, transportCrypto, dbExecutor, scheduler, clock, transportId,
				maxLatency);
		transportKeyManager.start(txn);
	}

	@Test
	public void testKeysAreRotatedWhenAddingContact() throws Exception {
		boolean alice = random.nextBoolean();
		TransportKeys transportKeys = createTransportKeys(999, 0);
		TransportKeys rotated = createTransportKeys(1000, 0);
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(transportCrypto).deriveTransportKeys(transportId, masterKey,
					999, alice);
			will(returnValue(transportKeys));
			// Get the current time (1 ms after start of rotation period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(rotationPeriodLength * 1000 + 1));
			// Rotate the transport keys
			oneOf(transportCrypto).rotateTransportKeys(transportKeys, 1000);
			will(returnValue(rotated));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(transportCrypto).encodeTag(
						with(any(byte[].class)), with(tagKey),
						with(PROTOCOL_VERSION), with(i));
				will(new EncodeTagAction());
			}
			// Save the keys
			oneOf(db).addTransportKeys(txn, contactId, rotated);
			will(returnValue(keySetId));
		}});

		TransportKeyManager transportKeyManager = new TransportKeyManagerImpl(
				db, transportCrypto, dbExecutor, scheduler, clock, transportId,
				maxLatency);
		// The timestamp is 1 ms before the start of rotation period 1000
		long timestamp = rotationPeriodLength * 1000 - 1;
		transportKeyManager.addContact(txn, contactId, masterKey, timestamp,
				alice);
	}

	@Test
	public void testKeysAreRotatedWhenAddingUnboundKeys() throws Exception {
		boolean alice = random.nextBoolean();
		TransportKeys transportKeys = createTransportKeys(999, 0);
		TransportKeys rotated = createTransportKeys(1000, 0);
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(transportCrypto).deriveTransportKeys(transportId, masterKey,
					999, alice);
			will(returnValue(transportKeys));
			// Get the current time (1 ms after start of rotation period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(rotationPeriodLength * 1000 + 1));
			// Rotate the transport keys
			oneOf(transportCrypto).rotateTransportKeys(transportKeys, 1000);
			will(returnValue(rotated));
			// Save the keys
			oneOf(db).addTransportKeys(txn, null, rotated);
			will(returnValue(keySetId));
		}});

		TransportKeyManager transportKeyManager = new TransportKeyManagerImpl(
				db, transportCrypto, dbExecutor, scheduler, clock, transportId,
				maxLatency);
		// The timestamp is 1 ms before the start of rotation period 1000
		long timestamp = rotationPeriodLength * 1000 - 1;
		assertEquals(keySetId, transportKeyManager.addUnboundKeys(txn,
				masterKey, timestamp, alice));
	}

	@Test
	public void testOutgoingStreamContextIsNullIfContactIsNotFound()
			throws Exception {
		Transaction txn = new Transaction(null, false);

		TransportKeyManager transportKeyManager = new TransportKeyManagerImpl(
				db, transportCrypto, dbExecutor, scheduler, clock, transportId,
				maxLatency);
		assertNull(transportKeyManager.getStreamContext(txn, contactId));
	}

	@Test
	public void testOutgoingStreamContextIsNullIfStreamCounterIsExhausted()
			throws Exception {
		boolean alice = random.nextBoolean();
		// The stream counter has been exhausted
		TransportKeys transportKeys = createTransportKeys(1000,
				MAX_32_BIT_UNSIGNED + 1);
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(transportCrypto).deriveTransportKeys(transportId, masterKey,
					1000, alice);
			will(returnValue(transportKeys));
			// Get the current time (the start of rotation period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(rotationPeriodLength * 1000));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(transportCrypto).encodeTag(
						with(any(byte[].class)), with(tagKey),
						with(PROTOCOL_VERSION), with(i));
				will(new EncodeTagAction());
			}
			// Rotate the transport keys (the keys are unaffected)
			oneOf(transportCrypto).rotateTransportKeys(transportKeys, 1000);
			will(returnValue(transportKeys));
			// Save the keys
			oneOf(db).addTransportKeys(txn, contactId, transportKeys);
			will(returnValue(keySetId));
		}});

		TransportKeyManager transportKeyManager = new TransportKeyManagerImpl(
				db, transportCrypto, dbExecutor, scheduler, clock, transportId,
				maxLatency);
		// The timestamp is at the start of rotation period 1000
		long timestamp = rotationPeriodLength * 1000;
		transportKeyManager.addContact(txn, contactId, masterKey, timestamp,
				alice);
		assertNull(transportKeyManager.getStreamContext(txn, contactId));
	}

	@Test
	public void testOutgoingStreamCounterIsIncremented() throws Exception {
		boolean alice = random.nextBoolean();
		// The stream counter can be used one more time before being exhausted
		TransportKeys transportKeys = createTransportKeys(1000,
				MAX_32_BIT_UNSIGNED);
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(transportCrypto).deriveTransportKeys(transportId, masterKey,
					1000, alice);
			will(returnValue(transportKeys));
			// Get the current time (the start of rotation period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(rotationPeriodLength * 1000));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(transportCrypto).encodeTag(
						with(any(byte[].class)), with(tagKey),
						with(PROTOCOL_VERSION), with(i));
				will(new EncodeTagAction());
			}
			// Rotate the transport keys (the keys are unaffected)
			oneOf(transportCrypto).rotateTransportKeys(transportKeys, 1000);
			will(returnValue(transportKeys));
			// Save the keys
			oneOf(db).addTransportKeys(txn, contactId, transportKeys);
			will(returnValue(keySetId));
			// Increment the stream counter
			oneOf(db).incrementStreamCounter(txn, contactId, transportId, 1000);
		}});

		TransportKeyManager transportKeyManager = new TransportKeyManagerImpl(
				db, transportCrypto, dbExecutor, scheduler, clock, transportId,
				maxLatency);
		// The timestamp is at the start of rotation period 1000
		long timestamp = rotationPeriodLength * 1000;
		transportKeyManager.addContact(txn, contactId, masterKey, timestamp,
				alice);
		// The first request should return a stream context
		StreamContext ctx = transportKeyManager.getStreamContext(txn,
				contactId);
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(transportId, ctx.getTransportId());
		assertEquals(tagKey, ctx.getTagKey());
		assertEquals(headerKey, ctx.getHeaderKey());
		assertEquals(MAX_32_BIT_UNSIGNED, ctx.getStreamNumber());
		// The second request should return null, the counter is exhausted
		assertNull(transportKeyManager.getStreamContext(txn, contactId));
	}

	@Test
	public void testIncomingStreamContextIsNullIfTagIsNotFound()
			throws Exception {
		boolean alice = random.nextBoolean();
		TransportKeys transportKeys = createTransportKeys(1000, 0);
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(transportCrypto).deriveTransportKeys(transportId, masterKey,
					1000, alice);
			will(returnValue(transportKeys));
			// Get the current time (the start of rotation period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(rotationPeriodLength * 1000));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(transportCrypto).encodeTag(
						with(any(byte[].class)), with(tagKey),
						with(PROTOCOL_VERSION), with(i));
				will(new EncodeTagAction());
			}
			// Rotate the transport keys (the keys are unaffected)
			oneOf(transportCrypto).rotateTransportKeys(transportKeys, 1000);
			will(returnValue(transportKeys));
			// Save the keys
			oneOf(db).addTransportKeys(txn, contactId, transportKeys);
			will(returnValue(keySetId));
		}});

		TransportKeyManager transportKeyManager = new TransportKeyManagerImpl(
				db, transportCrypto, dbExecutor, scheduler, clock, transportId,
				maxLatency);
		// The timestamp is at the start of rotation period 1000
		long timestamp = rotationPeriodLength * 1000;
		transportKeyManager.addContact(txn, contactId, masterKey, timestamp,
				alice);
		assertNull(transportKeyManager.getStreamContext(txn,
				new byte[TAG_LENGTH]));
	}

	@Test
	public void testTagIsNotRecognisedTwice() throws Exception {
		boolean alice = random.nextBoolean();
		TransportKeys transportKeys = createTransportKeys(1000, 0);
		// Keep a copy of the tags
		List<byte[]> tags = new ArrayList<>();
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(transportCrypto).deriveTransportKeys(transportId, masterKey,
					1000, alice);
			will(returnValue(transportKeys));
			// Get the current time (the start of rotation period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(rotationPeriodLength * 1000));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(transportCrypto).encodeTag(
						with(any(byte[].class)), with(tagKey),
						with(PROTOCOL_VERSION), with(i));
				will(new EncodeTagAction(tags));
			}
			// Rotate the transport keys (the keys are unaffected)
			oneOf(transportCrypto).rotateTransportKeys(transportKeys, 1000);
			will(returnValue(transportKeys));
			// Save the keys
			oneOf(db).addTransportKeys(txn, contactId, transportKeys);
			will(returnValue(keySetId));
			// Encode a new tag after sliding the window
			oneOf(transportCrypto).encodeTag(with(any(byte[].class)),
					with(tagKey), with(PROTOCOL_VERSION),
					with((long) REORDERING_WINDOW_SIZE));
			will(new EncodeTagAction(tags));
			// Save the reordering window (previous rotation period, base 1)
			oneOf(db).setReorderingWindow(txn, keySetId, transportId, 999,
					1, new byte[REORDERING_WINDOW_SIZE / 8]);
		}});

		TransportKeyManager transportKeyManager = new TransportKeyManagerImpl(
				db, transportCrypto, dbExecutor, scheduler, clock, transportId,
				maxLatency);
		// The timestamp is at the start of rotation period 1000
		long timestamp = rotationPeriodLength * 1000;
		transportKeyManager.addContact(txn, contactId, masterKey, timestamp,
				alice);
		// Use the first tag (previous rotation period, stream number 0)
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
	public void testKeysAreRotatedToCurrentPeriod() throws Exception {
		TransportKeys transportKeys = createTransportKeys(1000, 0);
		TransportKeys transportKeys1 = createTransportKeys(1000, 0);
		Collection<KeySet> loaded = asList(
				new KeySet(keySetId, contactId, transportKeys),
				new KeySet(keySetId1, null, transportKeys1)
		);
		TransportKeys rotated = createTransportKeys(1001, 0);
		TransportKeys rotated1 = createTransportKeys(1001, 0);
		Transaction txn = new Transaction(null, false);
		Transaction txn1 = new Transaction(null, false);

		context.checking(new Expectations() {{
			// Get the current time (the start of rotation period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(rotationPeriodLength * 1000));
			// Load the transport keys
			oneOf(db).getTransportKeys(txn, transportId);
			will(returnValue(loaded));
			// Rotate the transport keys (the keys are unaffected)
			oneOf(transportCrypto).rotateTransportKeys(transportKeys, 1000);
			will(returnValue(transportKeys));
			oneOf(transportCrypto).rotateTransportKeys(transportKeys1, 1000);
			will(returnValue(transportKeys1));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(transportCrypto).encodeTag(
						with(any(byte[].class)), with(tagKey),
						with(PROTOCOL_VERSION), with(i));
				will(new EncodeTagAction());
			}
			// Schedule key rotation at the start of the next rotation period
			oneOf(scheduler).schedule(with(any(Runnable.class)),
					with(rotationPeriodLength), with(MILLISECONDS));
			will(new RunAction());
			oneOf(dbExecutor).execute(with(any(Runnable.class)));
			will(new RunAction());
			// Start a transaction for key rotation
			oneOf(db).startTransaction(false);
			will(returnValue(txn1));
			// Get the current time (the start of rotation period 1001)
			oneOf(clock).currentTimeMillis();
			will(returnValue(rotationPeriodLength * 1001));
			// Rotate the transport keys
			oneOf(transportCrypto).rotateTransportKeys(
					with(any(TransportKeys.class)), with(1001L));
			will(returnValue(rotated));
			oneOf(transportCrypto).rotateTransportKeys(
					with(any(TransportKeys.class)), with(1001L));
			will(returnValue(rotated1));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(transportCrypto).encodeTag(
						with(any(byte[].class)), with(tagKey),
						with(PROTOCOL_VERSION), with(i));
				will(new EncodeTagAction());
			}
			// Save the keys that were rotated
			oneOf(db).updateTransportKeys(txn1, asList(
					new KeySet(keySetId, contactId, rotated),
					new KeySet(keySetId1, null, rotated1)
			));
			// Schedule key rotation at the start of the next rotation period
			oneOf(scheduler).schedule(with(any(Runnable.class)),
					with(rotationPeriodLength), with(MILLISECONDS));
			// Commit the key rotation transaction
			oneOf(db).commitTransaction(txn1);
			oneOf(db).endTransaction(txn1);
		}});

		TransportKeyManager transportKeyManager = new TransportKeyManagerImpl(
				db, transportCrypto, dbExecutor, scheduler, clock, transportId,
				maxLatency);
		transportKeyManager.start(txn);
	}

	private TransportKeys createTransportKeys(long rotationPeriod,
			long streamCounter) {
		IncomingKeys inPrev = new IncomingKeys(tagKey, headerKey,
				rotationPeriod - 1);
		IncomingKeys inCurr = new IncomingKeys(tagKey, headerKey,
				rotationPeriod);
		IncomingKeys inNext = new IncomingKeys(tagKey, headerKey,
				rotationPeriod + 1);
		OutgoingKeys outCurr = new OutgoingKeys(tagKey, headerKey,
				rotationPeriod, streamCounter);
		return new TransportKeys(transportId, inPrev, inCurr, inNext, outCurr);
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
		public Object invoke(Invocation invocation) throws Throwable {
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
