package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;
import static org.junit.Assert.assertArrayEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
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
import org.junit.Before;
import org.junit.Test;

public class KeyManagerImplTest extends BriarTestCase {

	private final Random random = new Random();
	private final ContactId contactId;
	private final TransportId transportId;
	private final long maxLatency;
	private final long rotationPeriodLength;
	private final byte[] secret0, secret1, secret2, secret3;
	private final long epoch = 1000L * 1000L * 1000L * 1000L;

	public KeyManagerImplTest() {
		contactId = new ContactId(234);
		transportId = new TransportId(TestUtils.getRandomId());
		maxLatency = 2 * 60 * 1000; // 2 minutes
		rotationPeriodLength = maxLatency + MAX_CLOCK_DIFFERENCE;
		secret0 = new byte[32];
		secret1 = new byte[32];
		secret2 = new byte[32];
		secret3 = new byte[32];
	}

	@Before
	public void setUp() {
		random.nextBytes(secret0);
		random.nextBytes(secret1);
		random.nextBytes(secret2);
		random.nextBytes(secret3);
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
			will(returnValue(epoch));
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
		Endpoint ep = new Endpoint(contactId, transportId, epoch, true);
		final TemporarySecret s0 = new TemporarySecret(ep, 0, secret0);
		final TemporarySecret s1 = new TemporarySecret(ep, 1, secret1);
		final TemporarySecret s2 = new TemporarySecret(ep, 2, secret2);
		context.checking(new Expectations() {{
			// start()
			oneOf(db).addListener(with(any(DatabaseListener.class)));
			oneOf(db).getSecrets();
			will(returnValue(Arrays.asList(s0, s1, s2)));
			oneOf(db).getTransportLatencies();
			will(returnValue(Collections.singletonMap(transportId,
					maxLatency)));
			// The current time is the second secret's activation time
			oneOf(clock).currentTimeMillis();
			will(returnValue(epoch));
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
	public void testLoadSecretsAtNewActivationTime() throws Exception {
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
		Endpoint ep = new Endpoint(contactId, transportId, epoch, true);
		final TemporarySecret s0 = new TemporarySecret(ep, 0, secret0);
		final TemporarySecret s1 = new TemporarySecret(ep, 1, secret1);
		final TemporarySecret s2 = new TemporarySecret(ep, 2, secret2);
		// A fourth secret should be derived and stored
		final TemporarySecret s3 = new TemporarySecret(ep, 3, secret3);
		context.checking(new Expectations() {{
			// start()
			oneOf(db).addListener(with(any(DatabaseListener.class)));
			oneOf(db).getSecrets();
			will(returnValue(Arrays.asList(s0, s1, s2)));
			oneOf(db).getTransportLatencies();
			will(returnValue(Collections.singletonMap(transportId,
					maxLatency)));
			// The current time is the third secret's activation time
			oneOf(clock).currentTimeMillis();
			will(returnValue(epoch + rotationPeriodLength));
			// A fourth secret should be derived and stored
			oneOf(crypto).deriveNextSecret(secret0, 1);
			will(returnValue(secret1.clone()));
			oneOf(crypto).deriveNextSecret(secret1, 2);
			will(returnValue(secret2.clone()));
			oneOf(crypto).deriveNextSecret(secret2, 3);
			will(returnValue(secret3));
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
		// The dead secret should have been erased
		assertArrayEquals(new byte[32], secret0);
		keyManager.stop();

		context.assertIsSatisfied();
	}
}
