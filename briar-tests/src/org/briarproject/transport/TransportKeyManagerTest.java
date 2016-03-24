package org.briarproject.transport;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.system.Clock;
import org.briarproject.api.system.Timer;
import org.briarproject.api.transport.IncomingKeys;
import org.briarproject.api.transport.OutgoingKeys;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.TransportKeys;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimerTask;

import static org.briarproject.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;
import static org.briarproject.api.transport.TransportConstants.REORDERING_WINDOW_SIZE;
import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;
import static org.briarproject.util.ByteUtils.MAX_32_BIT_UNSIGNED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class TransportKeyManagerTest extends BriarTestCase {

	private final TransportId transportId = new TransportId("id");
	private final long maxLatency = 30 * 1000; // 30 seconds
	private final long rotationPeriodLength = maxLatency + MAX_CLOCK_DIFFERENCE;
	private final ContactId contactId = new ContactId(123);
	private final ContactId contactId1 = new ContactId(234);
	private final SecretKey tagKey = TestUtils.createSecretKey();
	private final SecretKey headerKey = TestUtils.createSecretKey();
	private final SecretKey masterKey = TestUtils.createSecretKey();
	private final Random random = new Random();

	@Test
	public void testKeysAreRotatedAtStartup() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final CryptoComponent crypto = context.mock(CryptoComponent.class);
		final Timer timer = context.mock(Timer.class);
		final Clock clock = context.mock(Clock.class);
		final Transaction txn = new Transaction(null, true);
		final Map<ContactId, TransportKeys> loaded =
				new LinkedHashMap<ContactId, TransportKeys>();
		final TransportKeys shouldRotate = createTransportKeys(900, 0);
		final TransportKeys shouldNotRotate = createTransportKeys(1000, 0);
		loaded.put(contactId, shouldRotate);
		loaded.put(contactId1, shouldNotRotate);
		final TransportKeys rotated = createTransportKeys(1000, 0);
		final Transaction txn1 = new Transaction(null, false);
		context.checking(new Expectations() {{
			// Get the current time (1 ms after start of rotation period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(rotationPeriodLength * 1000 + 1));
			// Load the transport keys
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getTransportKeys(txn, transportId);
			will(returnValue(loaded));
			oneOf(db).endTransaction(txn);
			// Rotate the transport keys
			oneOf(crypto).rotateTransportKeys(shouldRotate, 1000);
			will(returnValue(rotated));
			oneOf(crypto).rotateTransportKeys(shouldNotRotate, 1000);
			will(returnValue(shouldNotRotate));
			// Encode the tags (3 sets per contact)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(6).of(crypto).encodeTag(with(any(byte[].class)),
						with(tagKey), with(i));
				will(new EncodeTagAction());
			}
			// Save the keys that were rotated
			oneOf(db).startTransaction(false);
			will(returnValue(txn1));
			oneOf(db).updateTransportKeys(txn1,
					Collections.singletonMap(contactId, rotated));
			oneOf(db).endTransaction(txn1);
			// Schedule key rotation at the start of the next rotation period
			oneOf(timer).schedule(with(any(TimerTask.class)),
					with(rotationPeriodLength - 1));
		}});

		TransportKeyManager transportKeyManager = new TransportKeyManager(db,
				crypto, timer, clock, transportId, maxLatency);
		transportKeyManager.start();

		context.assertIsSatisfied();
	}

	@Test
	public void testKeysAreRotatedWhenAddingContact() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final CryptoComponent crypto = context.mock(CryptoComponent.class);
		final Timer timer = context.mock(Timer.class);
		final Clock clock = context.mock(Clock.class);
		final boolean alice = true;
		final TransportKeys transportKeys = createTransportKeys(999, 0);
		final TransportKeys rotated = createTransportKeys(1000, 0);
		final Transaction txn = new Transaction(null, false);
		context.checking(new Expectations() {{
			oneOf(crypto).deriveTransportKeys(transportId, masterKey, 999,
					alice);
			will(returnValue(transportKeys));
			// Get the current time (1 ms after start of rotation period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(rotationPeriodLength * 1000 + 1));
			// Rotate the transport keys
			oneOf(crypto).rotateTransportKeys(transportKeys, 1000);
			will(returnValue(rotated));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(crypto).encodeTag(with(any(byte[].class)),
						with(tagKey), with(i));
				will(new EncodeTagAction());
			}
			// Save the keys
			oneOf(db).addTransportKeys(txn, contactId, rotated);
		}});

		TransportKeyManager transportKeyManager = new TransportKeyManager(db,
				crypto, timer, clock, transportId, maxLatency);
		// The timestamp is 1 ms before the start of rotation period 1000
		long timestamp = rotationPeriodLength * 1000 - 1;
		transportKeyManager.addContact(txn, contactId, masterKey, timestamp,
				alice);

		context.assertIsSatisfied();
	}

	@Test
	public void testOutgoingStreamContextIsNullIfContactIsNotFound()
			throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final CryptoComponent crypto = context.mock(CryptoComponent.class);
		final Timer timer = context.mock(Timer.class);
		final Clock clock = context.mock(Clock.class);

		TransportKeyManager transportKeyManager = new TransportKeyManager(db,
				crypto, timer, clock, transportId, maxLatency);
		assertNull(transportKeyManager.getStreamContext(contactId));

		context.assertIsSatisfied();
	}

	@Test
	public void testOutgoingStreamContextIsNullIfStreamCounterIsExhausted()
			throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final CryptoComponent crypto = context.mock(CryptoComponent.class);
		final Timer timer = context.mock(Timer.class);
		final Clock clock = context.mock(Clock.class);
		final boolean alice = true;
		// The stream counter has been exhausted
		final TransportKeys transportKeys = createTransportKeys(1000,
				MAX_32_BIT_UNSIGNED + 1);
		final Transaction txn = new Transaction(null, false);
		context.checking(new Expectations() {{
			oneOf(crypto).deriveTransportKeys(transportId, masterKey, 1000,
					alice);
			will(returnValue(transportKeys));
			// Get the current time (the start of rotation period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(rotationPeriodLength * 1000));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(crypto).encodeTag(with(any(byte[].class)),
						with(tagKey), with(i));
				will(new EncodeTagAction());
			}
			// Rotate the transport keys (the keys are unaffected)
			oneOf(crypto).rotateTransportKeys(transportKeys, 1000);
			will(returnValue(transportKeys));
			// Save the keys
			oneOf(db).addTransportKeys(txn, contactId, transportKeys);
		}});

		TransportKeyManager transportKeyManager = new TransportKeyManager(db,
				crypto, timer, clock, transportId, maxLatency);
		// The timestamp is at the start of rotation period 1000
		long timestamp = rotationPeriodLength * 1000;
		transportKeyManager.addContact(txn, contactId, masterKey, timestamp,
				alice);
		assertNull(transportKeyManager.getStreamContext(contactId));

		context.assertIsSatisfied();
	}

	@Test
	public void testOutgoingStreamCounterIsIncremented() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final CryptoComponent crypto = context.mock(CryptoComponent.class);
		final Timer timer = context.mock(Timer.class);
		final Clock clock = context.mock(Clock.class);
		final boolean alice = true;
		// The stream counter can be used one more time before being exhausted
		final TransportKeys transportKeys = createTransportKeys(1000,
				MAX_32_BIT_UNSIGNED);
		final Transaction txn = new Transaction(null, false);
		final Transaction txn1 = new Transaction(null, false);
		context.checking(new Expectations() {{
			oneOf(crypto).deriveTransportKeys(transportId, masterKey, 1000,
					alice);
			will(returnValue(transportKeys));
			// Get the current time (the start of rotation period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(rotationPeriodLength * 1000));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(crypto).encodeTag(with(any(byte[].class)),
						with(tagKey), with(i));
				will(new EncodeTagAction());
			}
			// Rotate the transport keys (the keys are unaffected)
			oneOf(crypto).rotateTransportKeys(transportKeys, 1000);
			will(returnValue(transportKeys));
			// Save the keys
			oneOf(db).addTransportKeys(txn, contactId, transportKeys);
			// Increment the stream counter
			oneOf(db).startTransaction(false);
			will(returnValue(txn1));
			oneOf(db).incrementStreamCounter(txn1, contactId, transportId,
					1000);
			oneOf(db).endTransaction(txn1);
		}});

		TransportKeyManager transportKeyManager = new TransportKeyManager(db,
				crypto, timer, clock, transportId, maxLatency);
		// The timestamp is at the start of rotation period 1000
		long timestamp = rotationPeriodLength * 1000;
		transportKeyManager.addContact(txn, contactId, masterKey, timestamp,
				alice);
		// The first request should return a stream context
		StreamContext ctx = transportKeyManager.getStreamContext(contactId);
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(transportId, ctx.getTransportId());
		assertEquals(tagKey, ctx.getTagKey());
		assertEquals(headerKey, ctx.getHeaderKey());
		assertEquals(MAX_32_BIT_UNSIGNED, ctx.getStreamNumber());
		// The second request should return null, the counter is exhausted
		assertNull(transportKeyManager.getStreamContext(contactId));

		context.assertIsSatisfied();
	}

	@Test
	public void testIncomingStreamContextIsNullIfTagIsNotFound()
			throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final CryptoComponent crypto = context.mock(CryptoComponent.class);
		final Timer timer = context.mock(Timer.class);
		final Clock clock = context.mock(Clock.class);
		final boolean alice = true;
		final TransportKeys transportKeys = createTransportKeys(1000, 0);
		final Transaction txn = new Transaction(null, false);
		context.checking(new Expectations() {{
			oneOf(crypto).deriveTransportKeys(transportId, masterKey, 1000,
					alice);
			will(returnValue(transportKeys));
			// Get the current time (the start of rotation period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(rotationPeriodLength * 1000));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(crypto).encodeTag(with(any(byte[].class)),
						with(tagKey), with(i));
				will(new EncodeTagAction());
			}
			// Rotate the transport keys (the keys are unaffected)
			oneOf(crypto).rotateTransportKeys(transportKeys, 1000);
			will(returnValue(transportKeys));
			// Save the keys
			oneOf(db).addTransportKeys(txn, contactId, transportKeys);
		}});

		TransportKeyManager transportKeyManager = new TransportKeyManager(db,
				crypto, timer, clock, transportId, maxLatency);
		// The timestamp is at the start of rotation period 1000
		long timestamp = rotationPeriodLength * 1000;
		transportKeyManager.addContact(txn, contactId, masterKey, timestamp,
				alice);
		assertNull(transportKeyManager.getStreamContext(new byte[TAG_LENGTH]));

		context.assertIsSatisfied();
	}

	@Test
	public void testTagIsNotRecognisedTwice() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final CryptoComponent crypto = context.mock(CryptoComponent.class);
		final Timer timer = context.mock(Timer.class);
		final Clock clock = context.mock(Clock.class);
		final boolean alice = true;
		final TransportKeys transportKeys = createTransportKeys(1000, 0);
		final Transaction txn = new Transaction(null, false);
		final Transaction txn1 = new Transaction(null, false);
		// Keep a copy of the tags
		final List<byte[]> tags = new ArrayList<byte[]>();
		context.checking(new Expectations() {{
			oneOf(crypto).deriveTransportKeys(transportId, masterKey, 1000,
					alice);
			will(returnValue(transportKeys));
			// Get the current time (the start of rotation period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(rotationPeriodLength * 1000));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(crypto).encodeTag(with(any(byte[].class)),
						with(tagKey), with(i));
				will(new EncodeTagAction(tags));
			}
			// Rotate the transport keys (the keys are unaffected)
			oneOf(crypto).rotateTransportKeys(transportKeys, 1000);
			will(returnValue(transportKeys));
			// Save the keys
			oneOf(db).addTransportKeys(txn, contactId, transportKeys);
			// Encode a new tag after sliding the window
			oneOf(crypto).encodeTag(with(any(byte[].class)),
					with(tagKey), with((long) REORDERING_WINDOW_SIZE));
			will(new EncodeTagAction(tags));
			// Save the reordering window (previous rotation period, base 1)
			oneOf(db).startTransaction(false);
			will(returnValue(txn1));
			oneOf(db).setReorderingWindow(txn1, contactId, transportId, 999,
					1, new byte[REORDERING_WINDOW_SIZE / 8]);
			oneOf(db).endTransaction(txn1);
		}});

		TransportKeyManager transportKeyManager = new TransportKeyManager(db,
				crypto, timer, clock, transportId, maxLatency);
		// The timestamp is at the start of rotation period 1000
		long timestamp = rotationPeriodLength * 1000;
		transportKeyManager.addContact(txn, contactId, masterKey, timestamp,
				alice);
		// Use the first tag (previous rotation period, stream number 0)
		assertEquals(REORDERING_WINDOW_SIZE * 3, tags.size());
		byte[] tag = tags.get(0);
		// The first request should return a stream context
		StreamContext ctx = transportKeyManager.getStreamContext(tag);
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(transportId, ctx.getTransportId());
		assertEquals(tagKey, ctx.getTagKey());
		assertEquals(headerKey, ctx.getHeaderKey());
		assertEquals(0L, ctx.getStreamNumber());
		// Another tag should have been encoded
		assertEquals(REORDERING_WINDOW_SIZE * 3 + 1, tags.size());
		// The second request should return null, the tag has already been used
		assertNull(transportKeyManager.getStreamContext(tag));

		context.assertIsSatisfied();
	}

	@Test
	public void testKeysAreRotatedToCurrentPeriod() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final CryptoComponent crypto = context.mock(CryptoComponent.class);
		final Timer timer = context.mock(Timer.class);
		final Clock clock = context.mock(Clock.class);
		final Transaction txn = new Transaction(null, true);
		final TransportKeys transportKeys = createTransportKeys(1000, 0);
		final Map<ContactId, TransportKeys> loaded =
				Collections.singletonMap(contactId, transportKeys);
		final TransportKeys rotated = createTransportKeys(1001, 0);
		final Transaction txn1 = new Transaction(null, false);
		context.checking(new Expectations() {{
			// Get the current time (the start of rotation period 1000)
			oneOf(clock).currentTimeMillis();
			will(returnValue(rotationPeriodLength * 1000));
			// Load the transport keys
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getTransportKeys(txn, transportId);
			will(returnValue(loaded));
			oneOf(db).endTransaction(txn);
			// Rotate the transport keys (the keys are unaffected)
			oneOf(crypto).rotateTransportKeys(transportKeys, 1000);
			will(returnValue(transportKeys));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(crypto).encodeTag(with(any(byte[].class)),
						with(tagKey), with(i));
				will(new EncodeTagAction());
			}
			// Schedule key rotation at the start of the next rotation period
			oneOf(timer).schedule(with(any(TimerTask.class)),
					with(rotationPeriodLength));
			will(new RunTimerTaskAction());
			// Get the current time (the start of rotation period 1001)
			oneOf(clock).currentTimeMillis();
			will(returnValue(rotationPeriodLength * 1001));
			// Rotate the transport keys
			oneOf(crypto).rotateTransportKeys(with(any(TransportKeys.class)),
					with(1001L));
			will(returnValue(rotated));
			// Encode the tags (3 sets)
			for (long i = 0; i < REORDERING_WINDOW_SIZE; i++) {
				exactly(3).of(crypto).encodeTag(with(any(byte[].class)),
						with(tagKey), with(i));
				will(new EncodeTagAction());
			}
			// Save the keys that were rotated
			oneOf(db).startTransaction(false);
			will(returnValue(txn1));
			oneOf(db).updateTransportKeys(txn1,
					Collections.singletonMap(contactId, rotated));
			oneOf(db).endTransaction(txn1);
			// Schedule key rotation at the start of the next rotation period
			oneOf(timer).schedule(with(any(TimerTask.class)),
					with(rotationPeriodLength));
		}});

		TransportKeyManager transportKeyManager = new TransportKeyManager(db,
				crypto, timer, clock, transportId, maxLatency);
		transportKeyManager.start();

		context.assertIsSatisfied();
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

	private static class RunTimerTaskAction implements Action {

		@Override
		public Object invoke(Invocation invocation) throws Throwable {
			TimerTask task = (TimerTask) invocation.getParameter(0);
			task.run();
			return null;
		}

		@Override
		public void describeTo(Description description) {
			description.appendText("schedules a timer task");
		}
	}
}
