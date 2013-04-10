package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;

import java.util.Arrays;
import java.util.Collections;
import java.util.TimerTask;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.clock.Timer;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.transport.ConnectionRecogniser;
import net.sf.briar.api.transport.Endpoint;
import net.sf.briar.api.transport.TemporarySecret;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

public class KeyManagerImplTest extends BriarTestCase {

	private static final long EPOCH = 1000L * 1000L * 1000L * 1000L;
	private static final long MAX_LATENCY = 2 * 60 * 1000; // 2 minutes
	private static final long ROTATION_PERIOD_LENGTH =
			MAX_LATENCY + MAX_CLOCK_DIFFERENCE;

	private final ContactId contactId;
	private final TransportId transportId;
	private final byte[] secret0, secret1, secret2, secret3, secret4;

	public KeyManagerImplTest() {
		contactId = new ContactId(234);
		transportId = new TransportId(TestUtils.getRandomId());
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
	}

	@Test
	public void testStartAndStop() throws Exception {
		Mockery context = new Mockery();
		final CryptoComponent crypto = context.mock(CryptoComponent.class);
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final ConnectionRecogniser connectionRecogniser =
				context.mock(ConnectionRecogniser.class);
		final Clock clock = context.mock(Clock.class);
		final Timer timer = context.mock(Timer.class);

		final KeyManagerImpl keyManager = new KeyManagerImpl(crypto, db,
				connectionRecogniser, clock, timer);

		context.checking(new Expectations() {{
			// start()
			oneOf(db).addListener(with(any(DatabaseListener.class)));
			oneOf(db).getSecrets();
			will(returnValue(Collections.emptyList()));
			oneOf(db).getTransportLatencies();
			will(returnValue(Collections.emptyMap()));
			oneOf(clock).currentTimeMillis();
			will(returnValue(EPOCH));
			oneOf(timer).scheduleAtFixedRate(with(any(TimerTask.class)),
					with(any(long.class)), with(any(long.class)));
			// stop()
			oneOf(db).removeListener(with(any(DatabaseListener.class)));
			oneOf(timer).cancel();
			oneOf(connectionRecogniser).removeSecrets();
		}});

		assertTrue(keyManager.start());
		keyManager.stop();

		context.assertIsSatisfied();
	}

	@Test
	public void testLoadSecretsAtEpoch() throws Exception {
		Mockery context = new Mockery();
		final CryptoComponent crypto = context.mock(CryptoComponent.class);
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final ConnectionRecogniser connectionRecogniser =
				context.mock(ConnectionRecogniser.class);
		final Clock clock = context.mock(Clock.class);
		final Timer timer = context.mock(Timer.class);

		final KeyManagerImpl keyManager = new KeyManagerImpl(crypto, db,
				connectionRecogniser, clock, timer);

		// The DB contains secrets for periods 0 - 2
		Endpoint ep = new Endpoint(contactId, transportId, EPOCH, true);
		final TemporarySecret s0 = new TemporarySecret(ep, 0, secret0.clone());
		final TemporarySecret s1 = new TemporarySecret(ep, 1, secret1.clone());
		final TemporarySecret s2 = new TemporarySecret(ep, 2, secret2.clone());

		context.checking(new Expectations() {{
			// start()
			oneOf(db).addListener(with(any(DatabaseListener.class)));
			oneOf(db).getSecrets();
			will(returnValue(Arrays.asList(s0, s1, s2)));
			oneOf(db).getTransportLatencies();
			will(returnValue(Collections.singletonMap(transportId,
					MAX_LATENCY)));
			// The current time is the epoch, the start of period 1
			oneOf(clock).currentTimeMillis();
			will(returnValue(EPOCH));
			// The secrets for periods 0 - 2 should be added to the recogniser
			oneOf(connectionRecogniser).addSecret(s0);
			oneOf(connectionRecogniser).addSecret(s1);
			oneOf(connectionRecogniser).addSecret(s2);
			oneOf(timer).scheduleAtFixedRate(with(any(TimerTask.class)),
					with(any(long.class)), with(any(long.class)));
			// stop()
			oneOf(db).removeListener(with(any(DatabaseListener.class)));
			oneOf(timer).cancel();
			oneOf(connectionRecogniser).removeSecrets();
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
		final ConnectionRecogniser connectionRecogniser =
				context.mock(ConnectionRecogniser.class);
		final Clock clock = context.mock(Clock.class);
		final Timer timer = context.mock(Timer.class);

		final KeyManagerImpl keyManager = new KeyManagerImpl(crypto, db,
				connectionRecogniser, clock, timer);

		// The DB contains secrets for periods 0 - 2
		Endpoint ep = new Endpoint(contactId, transportId, EPOCH, true);
		final TemporarySecret s0 = new TemporarySecret(ep, 0, secret0.clone());
		final TemporarySecret s1 = new TemporarySecret(ep, 1, secret1.clone());
		final TemporarySecret s2 = new TemporarySecret(ep, 2, secret2.clone());
		// The secret for period 3 should be derived and stored
		final TemporarySecret s3 = new TemporarySecret(ep, 3, secret3.clone());

		context.checking(new Expectations() {{
			// start()
			oneOf(db).addListener(with(any(DatabaseListener.class)));
			oneOf(db).getSecrets();
			will(returnValue(Arrays.asList(s0, s1, s2)));
			oneOf(db).getTransportLatencies();
			will(returnValue(Collections.singletonMap(transportId,
					MAX_LATENCY)));
			// The current time is the start of period 2
			oneOf(clock).currentTimeMillis();
			will(returnValue(EPOCH + ROTATION_PERIOD_LENGTH));
			// The secret for period 3 should be derived and stored
			oneOf(crypto).deriveNextSecret(secret0, 1);
			will(returnValue(secret1.clone()));
			oneOf(crypto).deriveNextSecret(secret1, 2);
			will(returnValue(secret2.clone()));
			oneOf(crypto).deriveNextSecret(secret2, 3);
			will(returnValue(secret3.clone()));
			oneOf(db).addSecrets(Arrays.asList(s3));
			// The secrets for periods 1 - 3 should be added to the recogniser
			oneOf(connectionRecogniser).addSecret(s1);
			oneOf(connectionRecogniser).addSecret(s2);
			oneOf(connectionRecogniser).addSecret(s3);
			oneOf(timer).scheduleAtFixedRate(with(any(TimerTask.class)),
					with(any(long.class)), with(any(long.class)));
			// stop()
			oneOf(db).removeListener(with(any(DatabaseListener.class)));
			oneOf(timer).cancel();
			oneOf(connectionRecogniser).removeSecrets();
		}});

		assertTrue(keyManager.start());
		keyManager.stop();

		context.assertIsSatisfied();
	}

	@Test
	public void testLoadSecretsAtStartOfPeriod3() throws Exception {
		Mockery context = new Mockery();
		final CryptoComponent crypto = context.mock(CryptoComponent.class);
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final ConnectionRecogniser connectionRecogniser =
				context.mock(ConnectionRecogniser.class);
		final Clock clock = context.mock(Clock.class);
		final Timer timer = context.mock(Timer.class);

		final KeyManagerImpl keyManager = new KeyManagerImpl(crypto, db,
				connectionRecogniser, clock, timer);

		// The DB contains secrets for periods 0 - 2
		Endpoint ep = new Endpoint(contactId, transportId, EPOCH, true);
		final TemporarySecret s0 = new TemporarySecret(ep, 0, secret0.clone());
		final TemporarySecret s1 = new TemporarySecret(ep, 1, secret1.clone());
		final TemporarySecret s2 = new TemporarySecret(ep, 2, secret2.clone());
		// The secrets for periods 3 and 4 should be derived and stored
		final TemporarySecret s3 = new TemporarySecret(ep, 3, secret3.clone());
		final TemporarySecret s4 = new TemporarySecret(ep, 4, secret4.clone());

		context.checking(new Expectations() {{
			// start()
			oneOf(db).addListener(with(any(DatabaseListener.class)));
			oneOf(db).getSecrets();
			will(returnValue(Arrays.asList(s0, s1, s2)));
			oneOf(db).getTransportLatencies();
			will(returnValue(Collections.singletonMap(transportId,
					MAX_LATENCY)));
			// The current time is the start of period 3
			oneOf(clock).currentTimeMillis();
			will(returnValue(EPOCH + 2 * ROTATION_PERIOD_LENGTH));
			// The secrets for periods 3 and 4 should be derived from secret 0
			oneOf(crypto).deriveNextSecret(secret0, 1);
			will(returnValue(secret1.clone()));
			oneOf(crypto).deriveNextSecret(secret1, 2);
			will(returnValue(secret2.clone()));
			oneOf(crypto).deriveNextSecret(secret2, 3);
			will(returnValue(secret3.clone()));
			oneOf(crypto).deriveNextSecret(secret3, 4);
			will(returnValue(secret4.clone()));
			// The secrets for periods 3 and 4 should be derived from secret 1
			oneOf(crypto).deriveNextSecret(secret1, 2);
			will(returnValue(secret2.clone()));
			oneOf(crypto).deriveNextSecret(secret2, 3);
			will(returnValue(secret3.clone()));
			oneOf(crypto).deriveNextSecret(secret3, 4);
			will(returnValue(secret4.clone()));
			// One copy of each of the new secrets should be stored
			oneOf(db).addSecrets(Arrays.asList(s3, s4));
			// The secrets for periods 2 - 3 should be added to the recogniser
			oneOf(connectionRecogniser).addSecret(s2);
			oneOf(connectionRecogniser).addSecret(s3);
			oneOf(connectionRecogniser).addSecret(s4);
			oneOf(timer).scheduleAtFixedRate(with(any(TimerTask.class)),
					with(any(long.class)), with(any(long.class)));
			// stop()
			oneOf(db).removeListener(with(any(DatabaseListener.class)));
			oneOf(timer).cancel();
			oneOf(connectionRecogniser).removeSecrets();
		}});

		assertTrue(keyManager.start());
		keyManager.stop();

		context.assertIsSatisfied();
	}
}
