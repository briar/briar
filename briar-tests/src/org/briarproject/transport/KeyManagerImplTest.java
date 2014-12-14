package org.briarproject.transport;

import static org.briarproject.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;
import static org.junit.Assert.assertArrayEquals;

import java.util.Arrays;
import java.util.Collections;

import org.briarproject.BriarTestCase;
import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.system.Clock;
import org.briarproject.api.system.Timer;
import org.briarproject.api.transport.Endpoint;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.TagRecogniser;
import org.briarproject.api.transport.TemporarySecret;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

public class KeyManagerImplTest extends BriarTestCase {

	private static final long EPOCH = 1000L * 1000L * 1000L * 1000L;
	private static final int MAX_LATENCY = 2 * 60 * 1000; // 2 minutes
	private static final int ROTATION_PERIOD =
			MAX_CLOCK_DIFFERENCE + MAX_LATENCY;

	private final ContactId contactId;
	private final TransportId transportId;
	private final byte[] secret0, secret1, secret2, secret3, secret4;
	private final byte[] initialSecret;

	public KeyManagerImplTest() {
		contactId = new ContactId(234);
		transportId = new TransportId("id");
		secret0 = new byte[32];
		secret1 = new byte[32];
		secret2 = new byte[32];
		secret3 = new byte[32];
		secret4 = new byte[32];
		for(int i = 0; i < secret0.length; i++) secret0[i] = 1;
		for(int i = 0; i < secret1.length; i++) secret1[i] = 2;
		for(int i = 0; i < secret2.length; i++) secret2[i] = 3;
		for(int i = 0; i < secret3.length; i++) secret3[i] = 4;
		for(int i = 0; i < secret4.length; i++) secret4[i] = 5;
		initialSecret = new byte[32];
		for(int i = 0; i < initialSecret.length; i++) initialSecret[i] = 123;
	}

	@Test
	public void testStartAndStop() throws Exception {
		Mockery context = new Mockery();
		final CryptoComponent crypto = context.mock(CryptoComponent.class);
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final EventBus eventBus = context.mock(EventBus.class);
		final TagRecogniser tagRecogniser = context.mock(TagRecogniser.class);
		final Clock clock = context.mock(Clock.class);
		final Timer timer = context.mock(Timer.class);

		final KeyManagerImpl keyManager = new KeyManagerImpl(crypto, db,
				eventBus, tagRecogniser, clock, timer);

		context.checking(new Expectations() {{
			// start()
			oneOf(eventBus).addListener(with(any(EventListener.class)));
			oneOf(db).getSecrets();
			will(returnValue(Collections.emptyList()));
			oneOf(db).getTransportLatencies();
			will(returnValue(Collections.emptyMap()));
			oneOf(clock).currentTimeMillis();
			will(returnValue(EPOCH));
			oneOf(timer).scheduleAtFixedRate(with(keyManager),
					with(any(long.class)), with(any(long.class)));
			// stop()
			oneOf(eventBus).removeListener(with(any(EventListener.class)));
			oneOf(timer).cancel();
			oneOf(tagRecogniser).removeSecrets();
		}});

		assertTrue(keyManager.start());
		keyManager.stop();

		context.assertIsSatisfied();
	}

	@Test
	public void testEndpointAdded() throws Exception {
		Mockery context = new Mockery();
		final CryptoComponent crypto = context.mock(CryptoComponent.class);
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final EventBus eventBus = context.mock(EventBus.class);
		final TagRecogniser tagRecogniser = context.mock(TagRecogniser.class);
		final Clock clock = context.mock(Clock.class);
		final Timer timer = context.mock(Timer.class);

		final KeyManagerImpl keyManager = new KeyManagerImpl(crypto, db,
				eventBus, tagRecogniser, clock, timer);

		// The secrets for periods 0 - 2 should be derived
		Endpoint ep = new Endpoint(contactId, transportId, EPOCH, true);
		final TemporarySecret s0 = new TemporarySecret(ep, 0, secret0.clone());
		final TemporarySecret s1 = new TemporarySecret(ep, 1, secret1.clone());
		final TemporarySecret s2 = new TemporarySecret(ep, 2, secret2.clone());

		context.checking(new Expectations() {{
			// start()
			oneOf(eventBus).addListener(with(any(EventListener.class)));
			oneOf(db).getSecrets();
			will(returnValue(Collections.emptyList()));
			oneOf(db).getTransportLatencies();
			will(returnValue(Collections.singletonMap(transportId,
					MAX_LATENCY)));
			oneOf(clock).currentTimeMillis();
			will(returnValue(EPOCH));
			oneOf(timer).scheduleAtFixedRate(with(keyManager),
					with(any(long.class)), with(any(long.class)));
			// endpointAdded() during rotation period 1
			oneOf(clock).currentTimeMillis();
			will(returnValue(EPOCH));
			oneOf(crypto).deriveNextSecret(initialSecret, 0);
			will(returnValue(secret0.clone()));
			oneOf(crypto).deriveNextSecret(secret0, 1);
			will(returnValue(secret1.clone()));
			oneOf(crypto).deriveNextSecret(secret1, 2);
			will(returnValue(secret2.clone()));
			oneOf(db).addSecrets(Arrays.asList(s0, s1, s2));
			// The secrets for periods 0 - 2 should be added to the recogniser
			oneOf(tagRecogniser).addSecret(s0);
			oneOf(tagRecogniser).addSecret(s1);
			oneOf(tagRecogniser).addSecret(s2);
			// stop()
			oneOf(eventBus).removeListener(with(any(EventListener.class)));
			oneOf(timer).cancel();
			oneOf(tagRecogniser).removeSecrets();
		}});

		assertTrue(keyManager.start());
		keyManager.endpointAdded(ep, MAX_LATENCY, initialSecret.clone());
		keyManager.stop();

		context.assertIsSatisfied();
	}

	@Test
	public void testEndpointAddedAndGetConnectionContext() throws Exception {
		Mockery context = new Mockery();
		final CryptoComponent crypto = context.mock(CryptoComponent.class);
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final EventBus eventBus = context.mock(EventBus.class);
		final TagRecogniser tagRecogniser = context.mock(TagRecogniser.class);
		final Clock clock = context.mock(Clock.class);
		final Timer timer = context.mock(Timer.class);

		final KeyManagerImpl keyManager = new KeyManagerImpl(crypto, db,
				eventBus, tagRecogniser, clock, timer);

		// The secrets for periods 0 - 2 should be derived
		Endpoint ep = new Endpoint(contactId, transportId, EPOCH, true);
		final TemporarySecret s0 = new TemporarySecret(ep, 0, secret0.clone());
		final TemporarySecret s1 = new TemporarySecret(ep, 1, secret1.clone());
		final TemporarySecret s2 = new TemporarySecret(ep, 2, secret2.clone());

		context.checking(new Expectations() {{
			// start()
			oneOf(eventBus).addListener(with(any(EventListener.class)));
			oneOf(db).getSecrets();
			will(returnValue(Collections.emptyList()));
			oneOf(db).getTransportLatencies();
			will(returnValue(Collections.singletonMap(transportId,
					MAX_LATENCY)));
			oneOf(clock).currentTimeMillis();
			will(returnValue(EPOCH));
			oneOf(timer).scheduleAtFixedRate(with(keyManager),
					with(any(long.class)), with(any(long.class)));
			// endpointAdded() during rotation period 1
			oneOf(clock).currentTimeMillis();
			will(returnValue(EPOCH));
			oneOf(crypto).deriveNextSecret(initialSecret, 0);
			will(returnValue(secret0.clone()));
			oneOf(crypto).deriveNextSecret(secret0, 1);
			will(returnValue(secret1.clone()));
			oneOf(crypto).deriveNextSecret(secret1, 2);
			will(returnValue(secret2.clone()));
			oneOf(db).addSecrets(Arrays.asList(s0, s1, s2));
			// The secrets for periods 0 - 2 should be added to the recogniser
			oneOf(tagRecogniser).addSecret(s0);
			oneOf(tagRecogniser).addSecret(s1);
			oneOf(tagRecogniser).addSecret(s2);
			// getConnectionContext()
			oneOf(db).incrementStreamCounter(contactId, transportId, 1);
			will(returnValue(0L));
			// stop()
			oneOf(eventBus).removeListener(with(any(EventListener.class)));
			oneOf(timer).cancel();
			oneOf(tagRecogniser).removeSecrets();
		}});

		assertTrue(keyManager.start());
		keyManager.endpointAdded(ep, MAX_LATENCY, initialSecret.clone());
		StreamContext ctx =
				keyManager.getStreamContext(contactId, transportId);
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(transportId, ctx.getTransportId());
		assertArrayEquals(secret1, ctx.getSecret());
		assertEquals(0, ctx.getStreamNumber());
		assertEquals(true, ctx.getAlice());
		keyManager.stop();

		context.assertIsSatisfied();
	}

	@Test
	public void testLoadSecretsAtEpoch() throws Exception {
		Mockery context = new Mockery();
		final CryptoComponent crypto = context.mock(CryptoComponent.class);
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final EventBus eventBus = context.mock(EventBus.class);
		final TagRecogniser tagRecogniser = context.mock(TagRecogniser.class);
		final Clock clock = context.mock(Clock.class);
		final Timer timer = context.mock(Timer.class);

		final KeyManagerImpl keyManager = new KeyManagerImpl(crypto, db,
				eventBus, tagRecogniser, clock, timer);

		// The DB contains the secrets for periods 0 - 2
		Endpoint ep = new Endpoint(contactId, transportId, EPOCH, true);
		final TemporarySecret s0 = new TemporarySecret(ep, 0, secret0.clone());
		final TemporarySecret s1 = new TemporarySecret(ep, 1, secret1.clone());
		final TemporarySecret s2 = new TemporarySecret(ep, 2, secret2.clone());

		context.checking(new Expectations() {{
			// start()
			oneOf(eventBus).addListener(with(any(EventListener.class)));
			oneOf(db).getSecrets();
			will(returnValue(Arrays.asList(s0, s1, s2)));
			oneOf(db).getTransportLatencies();
			will(returnValue(Collections.singletonMap(transportId,
					MAX_LATENCY)));
			// The current time is the epoch, the start of period 1
			oneOf(clock).currentTimeMillis();
			will(returnValue(EPOCH));
			// The secrets for periods 0 - 2 should be added to the recogniser
			oneOf(tagRecogniser).addSecret(s0);
			oneOf(tagRecogniser).addSecret(s1);
			oneOf(tagRecogniser).addSecret(s2);
			oneOf(timer).scheduleAtFixedRate(with(keyManager),
					with(any(long.class)), with(any(long.class)));
			// stop()
			oneOf(eventBus).removeListener(with(any(EventListener.class)));
			oneOf(timer).cancel();
			oneOf(tagRecogniser).removeSecrets();
		}});

		assertTrue(keyManager.start());
		keyManager.stop();

		context.assertIsSatisfied();
	}

	@Test
	public void testLoadSecretsAtStartOfPeriod2() throws Exception {
		Mockery context = new Mockery();
		final CryptoComponent crypto = context.mock(CryptoComponent.class);
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final EventBus eventBus = context.mock(EventBus.class);
		final TagRecogniser tagRecogniser = context.mock(TagRecogniser.class);
		final Clock clock = context.mock(Clock.class);
		final Timer timer = context.mock(Timer.class);

		final KeyManagerImpl keyManager = new KeyManagerImpl(crypto, db,
				eventBus, tagRecogniser, clock, timer);

		// The DB contains the secrets for periods 0 - 2
		Endpoint ep = new Endpoint(contactId, transportId, EPOCH, true);
		final TemporarySecret s0 = new TemporarySecret(ep, 0, secret0.clone());
		final TemporarySecret s1 = new TemporarySecret(ep, 1, secret1.clone());
		final TemporarySecret s2 = new TemporarySecret(ep, 2, secret2.clone());
		// The secret for period 3 should be derived and stored
		final TemporarySecret s3 = new TemporarySecret(ep, 3, secret3.clone());

		context.checking(new Expectations() {{
			// start()
			oneOf(eventBus).addListener(with(any(EventListener.class)));
			oneOf(db).getSecrets();
			will(returnValue(Arrays.asList(s0, s1, s2)));
			oneOf(db).getTransportLatencies();
			will(returnValue(Collections.singletonMap(transportId,
					MAX_LATENCY)));
			// The current time is the start of period 2
			oneOf(clock).currentTimeMillis();
			will(returnValue(EPOCH + ROTATION_PERIOD));
			// The secret for period 3 should be derived and stored
			oneOf(crypto).deriveNextSecret(secret0, 1);
			will(returnValue(secret1.clone()));
			oneOf(crypto).deriveNextSecret(secret1, 2);
			will(returnValue(secret2.clone()));
			oneOf(crypto).deriveNextSecret(secret2, 3);
			will(returnValue(secret3.clone()));
			oneOf(db).addSecrets(Arrays.asList(s3));
			// The secrets for periods 1 - 3 should be added to the recogniser
			oneOf(tagRecogniser).addSecret(s1);
			oneOf(tagRecogniser).addSecret(s2);
			oneOf(tagRecogniser).addSecret(s3);
			oneOf(timer).scheduleAtFixedRate(with(keyManager),
					with(any(long.class)), with(any(long.class)));
			// stop()
			oneOf(eventBus).removeListener(with(any(EventListener.class)));
			oneOf(timer).cancel();
			oneOf(tagRecogniser).removeSecrets();
		}});

		assertTrue(keyManager.start());
		keyManager.stop();

		context.assertIsSatisfied();
	}

	@Test
	public void testLoadSecretsAtEndOfPeriod3() throws Exception {
		Mockery context = new Mockery();
		final CryptoComponent crypto = context.mock(CryptoComponent.class);
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final EventBus eventBus = context.mock(EventBus.class);
		final TagRecogniser tagRecogniser = context.mock(TagRecogniser.class);
		final Clock clock = context.mock(Clock.class);
		final Timer timer = context.mock(Timer.class);

		final KeyManagerImpl keyManager = new KeyManagerImpl(crypto, db,
				eventBus, tagRecogniser, clock, timer);

		// The DB contains the secrets for periods 0 - 2
		Endpoint ep = new Endpoint(contactId, transportId, EPOCH, true);
		final TemporarySecret s0 = new TemporarySecret(ep, 0, secret0.clone());
		final TemporarySecret s1 = new TemporarySecret(ep, 1, secret1.clone());
		final TemporarySecret s2 = new TemporarySecret(ep, 2, secret2.clone());
		// The secrets for periods 3 and 4 should be derived and stored
		final TemporarySecret s3 = new TemporarySecret(ep, 3, secret3.clone());
		final TemporarySecret s4 = new TemporarySecret(ep, 4, secret4.clone());

		context.checking(new Expectations() {{
			// start()
			oneOf(eventBus).addListener(with(any(EventListener.class)));
			oneOf(db).getSecrets();
			will(returnValue(Arrays.asList(s0, s1, s2)));
			oneOf(db).getTransportLatencies();
			will(returnValue(Collections.singletonMap(transportId,
					MAX_LATENCY)));
			// The current time is the end of period 3
			oneOf(clock).currentTimeMillis();
			will(returnValue(EPOCH + 3 * ROTATION_PERIOD - 1));
			// The secrets for periods 3 and 4 should be derived from secret 1
			oneOf(crypto).deriveNextSecret(secret1, 2);
			will(returnValue(secret2.clone()));
			oneOf(crypto).deriveNextSecret(secret2, 3);
			will(returnValue(secret3.clone()));
			oneOf(crypto).deriveNextSecret(secret3, 4);
			will(returnValue(secret4.clone()));
			// The new secrets should be stored
			oneOf(db).addSecrets(Arrays.asList(s3, s4));
			// The secrets for periods 2 - 4 should be added to the recogniser
			oneOf(tagRecogniser).addSecret(s2);
			oneOf(tagRecogniser).addSecret(s3);
			oneOf(tagRecogniser).addSecret(s4);
			oneOf(timer).scheduleAtFixedRate(with(keyManager),
					with(any(long.class)), with(any(long.class)));
			// stop()
			oneOf(eventBus).removeListener(with(any(EventListener.class)));
			oneOf(timer).cancel();
			oneOf(tagRecogniser).removeSecrets();
		}});

		assertTrue(keyManager.start());
		keyManager.stop();

		context.assertIsSatisfied();
	}

	@Test
	public void testLoadSecretsAndRotateInSamePeriod() throws Exception {
		Mockery context = new Mockery();
		final CryptoComponent crypto = context.mock(CryptoComponent.class);
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final EventBus eventBus = context.mock(EventBus.class);
		final TagRecogniser tagRecogniser = context.mock(TagRecogniser.class);
		final Clock clock = context.mock(Clock.class);
		final Timer timer = context.mock(Timer.class);

		final KeyManagerImpl keyManager = new KeyManagerImpl(crypto, db,
				eventBus, tagRecogniser, clock, timer);

		// The DB contains the secrets for periods 0 - 2
		Endpoint ep = new Endpoint(contactId, transportId, EPOCH, true);
		final TemporarySecret s0 = new TemporarySecret(ep, 0, secret0.clone());
		final TemporarySecret s1 = new TemporarySecret(ep, 1, secret1.clone());
		final TemporarySecret s2 = new TemporarySecret(ep, 2, secret2.clone());

		context.checking(new Expectations() {{
			// start()
			oneOf(eventBus).addListener(with(any(EventListener.class)));
			oneOf(db).getSecrets();
			will(returnValue(Arrays.asList(s0, s1, s2)));
			oneOf(db).getTransportLatencies();
			will(returnValue(Collections.singletonMap(transportId,
					MAX_LATENCY)));
			// The current time is the epoch, the start of period 1
			oneOf(clock).currentTimeMillis();
			will(returnValue(EPOCH));
			// The secrets for periods 0 - 2 should be added to the recogniser
			oneOf(tagRecogniser).addSecret(s0);
			oneOf(tagRecogniser).addSecret(s1);
			oneOf(tagRecogniser).addSecret(s2);
			oneOf(timer).scheduleAtFixedRate(with(keyManager),
					with(any(long.class)), with(any(long.class)));
			// run() during period 1: the secrets should not be affected
			oneOf(clock).currentTimeMillis();
			will(returnValue(EPOCH + 1));
			// getConnectionContext()
			oneOf(db).incrementStreamCounter(contactId, transportId, 1);
			will(returnValue(0L));
			// stop()
			oneOf(eventBus).removeListener(with(any(EventListener.class)));
			oneOf(timer).cancel();
			oneOf(tagRecogniser).removeSecrets();
		}});

		assertTrue(keyManager.start());
		keyManager.run();
		StreamContext ctx =
				keyManager.getStreamContext(contactId, transportId);
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(transportId, ctx.getTransportId());
		assertArrayEquals(secret1, ctx.getSecret());
		assertEquals(0, ctx.getStreamNumber());
		assertEquals(true, ctx.getAlice());
		keyManager.stop();

		context.assertIsSatisfied();
	}

	@Test
	public void testLoadSecretsAndRotateInNextPeriod() throws Exception {
		Mockery context = new Mockery();
		final CryptoComponent crypto = context.mock(CryptoComponent.class);
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final EventBus eventBus = context.mock(EventBus.class);
		final TagRecogniser tagRecogniser = context.mock(TagRecogniser.class);
		final Clock clock = context.mock(Clock.class);
		final Timer timer = context.mock(Timer.class);

		final KeyManagerImpl keyManager = new KeyManagerImpl(crypto, db,
				eventBus, tagRecogniser, clock, timer);

		// The DB contains the secrets for periods 0 - 2
		Endpoint ep = new Endpoint(contactId, transportId, EPOCH, true);
		final TemporarySecret s0 = new TemporarySecret(ep, 0, secret0.clone());
		final TemporarySecret s1 = new TemporarySecret(ep, 1, secret1.clone());
		final TemporarySecret s2 = new TemporarySecret(ep, 2, secret2.clone());
		// The secret for period 3 should be derived and stored
		final TemporarySecret s3 = new TemporarySecret(ep, 3, secret3.clone());

		context.checking(new Expectations() {{
			// start()
			oneOf(eventBus).addListener(with(any(EventListener.class)));
			oneOf(db).getSecrets();
			will(returnValue(Arrays.asList(s0, s1, s2)));
			oneOf(db).getTransportLatencies();
			will(returnValue(Collections.singletonMap(transportId,
					MAX_LATENCY)));
			// The current time is the epoch, the start of period 1
			oneOf(clock).currentTimeMillis();
			will(returnValue(EPOCH));
			// The secrets for periods 0 - 2 should be added to the recogniser
			oneOf(tagRecogniser).addSecret(s0);
			oneOf(tagRecogniser).addSecret(s1);
			oneOf(tagRecogniser).addSecret(s2);
			oneOf(timer).scheduleAtFixedRate(with(keyManager),
					with(any(long.class)), with(any(long.class)));
			// run() during period 2: the secrets should be rotated
			oneOf(clock).currentTimeMillis();
			will(returnValue(EPOCH + ROTATION_PERIOD + 1));
			oneOf(crypto).deriveNextSecret(secret0, 1);
			will(returnValue(secret1.clone()));
			oneOf(crypto).deriveNextSecret(secret1, 2);
			will(returnValue(secret2.clone()));
			oneOf(crypto).deriveNextSecret(secret2, 3);
			will(returnValue(secret3.clone()));
			oneOf(tagRecogniser).removeSecret(contactId, transportId, 0);
			oneOf(db).addSecrets(Arrays.asList(s3));
			oneOf(tagRecogniser).addSecret(s3);
			// getConnectionContext()
			oneOf(db).incrementStreamCounter(contactId, transportId, 2);
			will(returnValue(0L));
			// stop()
			oneOf(eventBus).removeListener(with(any(EventListener.class)));
			oneOf(timer).cancel();
			oneOf(tagRecogniser).removeSecrets();
		}});

		assertTrue(keyManager.start());
		keyManager.run();
		StreamContext ctx =
				keyManager.getStreamContext(contactId, transportId);
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(transportId, ctx.getTransportId());
		assertArrayEquals(secret2, ctx.getSecret());
		assertEquals(0, ctx.getStreamNumber());
		assertEquals(true, ctx.getAlice());
		keyManager.stop();

		context.assertIsSatisfied();
	}

	@Test
	public void testLoadSecretsAndRotateAWholePeriodLate() throws Exception {
		Mockery context = new Mockery();
		final CryptoComponent crypto = context.mock(CryptoComponent.class);
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final EventBus eventBus = context.mock(EventBus.class);
		final TagRecogniser tagRecogniser = context.mock(TagRecogniser.class);
		final Clock clock = context.mock(Clock.class);
		final Timer timer = context.mock(Timer.class);

		final KeyManagerImpl keyManager = new KeyManagerImpl(crypto, db,
				eventBus, tagRecogniser, clock, timer);

		// The DB contains the secrets for periods 0 - 2
		Endpoint ep = new Endpoint(contactId, transportId, EPOCH, true);
		final TemporarySecret s0 = new TemporarySecret(ep, 0, secret0.clone());
		final TemporarySecret s1 = new TemporarySecret(ep, 1, secret1.clone());
		final TemporarySecret s2 = new TemporarySecret(ep, 2, secret2.clone());
		// The secrets for periods 3 and 4 should be derived and stored
		final TemporarySecret s3 = new TemporarySecret(ep, 3, secret3.clone());
		final TemporarySecret s4 = new TemporarySecret(ep, 4, secret4.clone());

		context.checking(new Expectations() {{
			// start()
			oneOf(eventBus).addListener(with(any(EventListener.class)));
			oneOf(db).getSecrets();
			will(returnValue(Arrays.asList(s0, s1, s2)));
			oneOf(db).getTransportLatencies();
			will(returnValue(Collections.singletonMap(transportId,
					MAX_LATENCY)));
			// The current time is the epoch, the start of period 1
			oneOf(clock).currentTimeMillis();
			will(returnValue(EPOCH));
			// The secrets for periods 0 - 2 should be added to the recogniser
			oneOf(tagRecogniser).addSecret(s0);
			oneOf(tagRecogniser).addSecret(s1);
			oneOf(tagRecogniser).addSecret(s2);
			oneOf(timer).scheduleAtFixedRate(with(keyManager),
					with(any(long.class)), with(any(long.class)));
			// run() during period 3 (late): the secrets should be rotated
			oneOf(clock).currentTimeMillis();
			will(returnValue(EPOCH + 2 * ROTATION_PERIOD + 1));
			oneOf(crypto).deriveNextSecret(secret1, 2);
			will(returnValue(secret2.clone()));
			oneOf(crypto).deriveNextSecret(secret2, 3);
			will(returnValue(secret3.clone()));
			oneOf(crypto).deriveNextSecret(secret3, 4);
			will(returnValue(secret4.clone()));
			oneOf(tagRecogniser).removeSecret(contactId, transportId, 0);
			oneOf(tagRecogniser).removeSecret(contactId, transportId, 1);
			oneOf(db).addSecrets(Arrays.asList(s3, s4));
			oneOf(tagRecogniser).addSecret(s3);
			oneOf(tagRecogniser).addSecret(s4);
			// getConnectionContext()
			oneOf(db).incrementStreamCounter(contactId, transportId, 3);
			will(returnValue(0L));
			// stop()
			oneOf(eventBus).removeListener(with(any(EventListener.class)));
			oneOf(timer).cancel();
			oneOf(tagRecogniser).removeSecrets();
		}});

		assertTrue(keyManager.start());
		keyManager.run();
		StreamContext ctx =
				keyManager.getStreamContext(contactId, transportId);
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(transportId, ctx.getTransportId());
		assertArrayEquals(secret3, ctx.getSecret());
		assertEquals(0, ctx.getStreamNumber());
		assertEquals(true, ctx.getAlice());
		keyManager.stop();

		context.assertIsSatisfied();
	}
}
