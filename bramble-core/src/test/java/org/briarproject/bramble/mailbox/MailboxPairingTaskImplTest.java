package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.mailbox.MailboxAuthToken;
import org.briarproject.bramble.api.mailbox.MailboxPairingState;
import org.briarproject.bramble.api.mailbox.MailboxPairingTask;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager;
import org.briarproject.bramble.api.mailbox.MailboxUpdate;
import org.briarproject.bramble.api.mailbox.MailboxUpdateManager;
import org.briarproject.bramble.api.mailbox.MailboxVersion;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.DbExpectations;
import org.briarproject.bramble.test.ImmediateExecutor;
import org.briarproject.bramble.test.PredicateMatcher;
import org.jmock.Expectations;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.singletonList;
import static org.briarproject.bramble.mailbox.MailboxTestUtils.getQrCodePayload;
import static org.briarproject.bramble.test.TestUtils.getContact;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MailboxPairingTaskImplTest extends BrambleMockTestCase {

	private final Executor executor = new ImmediateExecutor();
	private final DatabaseComponent db =
			context.mock(DatabaseComponent.class);
	private final CryptoComponent crypto = context.mock(CryptoComponent.class);
	private final Clock clock = context.mock(Clock.class);
	private final MailboxApi api = context.mock(MailboxApi.class);
	private final MailboxSettingsManager mailboxSettingsManager =
			context.mock(MailboxSettingsManager.class);
	private final MailboxUpdateManager mailboxUpdateManager =
			context.mock(MailboxUpdateManager.class);
	private final MailboxPairingTaskFactory factory =
			new MailboxPairingTaskFactoryImpl(executor, db, crypto, clock, api,
					mailboxSettingsManager, mailboxUpdateManager);

	private final String onion = getRandomString(56);
	private final byte[] onionBytes = getRandomBytes(32);
	private final MailboxAuthToken setupToken =
			new MailboxAuthToken(getRandomId());
	private final MailboxAuthToken ownerToken =
			new MailboxAuthToken(getRandomId());
	private final String validPayload =
			getQrCodePayload(onionBytes, setupToken.getBytes());
	private final long time = System.currentTimeMillis();
	private final MailboxProperties setupProperties = new MailboxProperties(
			onion, setupToken, new ArrayList<>());
	private final MailboxProperties ownerProperties = new MailboxProperties(
			onion, ownerToken, new ArrayList<>());

	@Test
	public void testInitialQrCodeReceivedState() {
		MailboxPairingTask task =
				factory.createPairingTask(getRandomString(42));
		task.addObserver(state ->
				assertTrue(state instanceof MailboxPairingState.QrCodeReceived)
		);
	}

	@Test
	public void testInvalidQrCode() {
		MailboxPairingTask task1 =
				factory.createPairingTask(getRandomString(42));
		task1.run();
		task1.addObserver(state ->
				assertTrue(state instanceof MailboxPairingState.InvalidQrCode)
		);

		String goodLength = "00" + getRandomString(63);
		MailboxPairingTask task2 = factory.createPairingTask(goodLength);
		task2.run();
		task2.addObserver(state ->
				assertTrue(state instanceof MailboxPairingState.InvalidQrCode)
		);
	}

	@Test
	public void testSuccessfulPairing() throws Exception {
		context.checking(new Expectations() {{
			oneOf(crypto).encodeOnion(onionBytes);
			will(returnValue(onion));
			oneOf(api).setup(with(matches(setupProperties)));
			will(returnValue(ownerProperties));
			oneOf(clock).currentTimeMillis();
			will(returnValue(time));
		}});
		Contact contact1 = getContact();
		Transaction txn = new Transaction(null, false);
		MailboxUpdate updateNoMailbox = new MailboxUpdate(
				singletonList(new MailboxVersion(47, 11)));
		context.checking(new DbExpectations() {{
			oneOf(db).transaction(with(false), withDbRunnable(txn));
			oneOf(mailboxSettingsManager).setOwnMailboxProperties(
					with(txn), with(matches(ownerProperties)));
			oneOf(mailboxSettingsManager).recordSuccessfulConnection(txn, time,
					ownerProperties.getServerSupports());
			oneOf(db).getContacts(txn);
			will(returnValue(singletonList(contact1)));
			oneOf(mailboxUpdateManager).getRemoteUpdate(txn,
					contact1.getId());
			will(returnValue(updateNoMailbox));
			oneOf(db).resetUnackedMessagesToSend(txn, contact1.getId());
		}});

		AtomicInteger i = new AtomicInteger(0);
		MailboxPairingTask task = factory.createPairingTask(validPayload);
		task.addObserver(state -> {
			if (i.get() == 0) {
				assertEquals(MailboxPairingState.QrCodeReceived.class,
						state.getClass());
			} else if (i.get() == 1) {
				assertEquals(MailboxPairingState.Pairing.class,
						state.getClass());
			} else if (i.get() == 2) {
				assertEquals(MailboxPairingState.Paired.class,
						state.getClass());
			} else fail("Unexpected change of state " + state.getClass());
			i.getAndIncrement();
		});
		task.run();
	}

	@Test
	public void testAlreadyPaired() throws Exception {
		testApiException(new MailboxApi.MailboxAlreadyPairedException(),
				MailboxPairingState.MailboxAlreadyPaired.class);
	}

	@Test
	public void testMailboxApiException() throws Exception {
		testApiException(new MailboxApi.ApiException(),
				MailboxPairingState.UnexpectedError.class);
	}

	@Test
	public void testApiIOException() throws Exception {
		testApiException(new IOException(),
				MailboxPairingState.ConnectionError.class);
	}

	private void testApiException(Exception e,
			Class<? extends MailboxPairingState> s) throws Exception {
		context.checking(new Expectations() {{
			oneOf(crypto).encodeOnion(onionBytes);
			will(returnValue(onion));
			oneOf(api).setup(with(matches(setupProperties)));
			will(throwException(e));
		}});

		MailboxPairingTask task = factory.createPairingTask(validPayload);
		task.run();
		task.addObserver(state -> assertEquals(state.getClass(), s));
	}

	@Test
	public void testDbException() throws Exception {
		context.checking(new Expectations() {{
			oneOf(crypto).encodeOnion(onionBytes);
			will(returnValue(onion));
			oneOf(api).setup(with(matches(setupProperties)));
			will(returnValue(ownerProperties));
			oneOf(clock).currentTimeMillis();
			will(returnValue(time));
		}});
		Transaction txn = new Transaction(null, false);
		context.checking(new DbExpectations() {{
			oneOf(db).transaction(with(false), withDbRunnable(txn));
			oneOf(mailboxSettingsManager).setOwnMailboxProperties(
					with(txn), with(matches(ownerProperties)));
			will(throwException(new DbException()));
		}});

		MailboxPairingTask task = factory.createPairingTask(validPayload);
		task.run();
		task.addObserver(state -> assertEquals(state.getClass(),
				MailboxPairingState.UnexpectedError.class));
	}

	private PredicateMatcher<MailboxProperties> matches(MailboxProperties p2) {
		return new PredicateMatcher<>(MailboxProperties.class, p1 ->
				p1.getAuthToken().equals(p2.getAuthToken()) &&
						p1.getOnion().equals(p2.getOnion()) &&
						p1.isOwner() == p2.isOwner() &&
						p1.getServerSupports().equals(p2.getServerSupports()));
	}

}
