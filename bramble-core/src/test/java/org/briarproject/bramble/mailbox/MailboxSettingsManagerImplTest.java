package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.mailbox.MailboxAuthToken;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager.MailboxHook;
import org.briarproject.bramble.api.mailbox.MailboxStatus;
import org.briarproject.bramble.api.mailbox.MailboxVersion;
import org.briarproject.bramble.api.mailbox.event.OwnMailboxConnectionStatusEvent;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Test;

import java.util.List;
import java.util.Random;

import static java.util.Arrays.asList;
import static org.briarproject.bramble.mailbox.MailboxSettingsManagerImpl.SETTINGS_KEY_ATTEMPTS;
import static org.briarproject.bramble.mailbox.MailboxSettingsManagerImpl.SETTINGS_KEY_LAST_ATTEMPT;
import static org.briarproject.bramble.mailbox.MailboxSettingsManagerImpl.SETTINGS_KEY_LAST_SUCCESS;
import static org.briarproject.bramble.mailbox.MailboxSettingsManagerImpl.SETTINGS_KEY_ONION;
import static org.briarproject.bramble.mailbox.MailboxSettingsManagerImpl.SETTINGS_KEY_SERVER_SUPPORTS;
import static org.briarproject.bramble.mailbox.MailboxSettingsManagerImpl.SETTINGS_KEY_TOKEN;
import static org.briarproject.bramble.mailbox.MailboxSettingsManagerImpl.SETTINGS_NAMESPACE;
import static org.briarproject.bramble.mailbox.MailboxSettingsManagerImpl.SETTINGS_UPLOADS_NAMESPACE;
import static org.briarproject.bramble.test.TestUtils.getEvent;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.hasEvent;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MailboxSettingsManagerImplTest extends BrambleMockTestCase {

	private final SettingsManager settingsManager =
			context.mock(SettingsManager.class);
	private final MailboxHook hook = context.mock(MailboxHook.class);

	private final MailboxSettingsManager manager =
			new MailboxSettingsManagerImpl(settingsManager);
	private final Random random = new Random();
	private final String onion = getRandomString(64);
	private final MailboxAuthToken token = new MailboxAuthToken(getRandomId());
	private final List<MailboxVersion> serverSupports =
			asList(new MailboxVersion(1, 0), new MailboxVersion(1, 1));
	private final MailboxProperties properties = new MailboxProperties(onion,
			token, serverSupports);
	private final int[] serverSupportsInts = {1, 0, 1, 1};
	private final Settings pairedSettings;
	private final ContactId contactId1 = new ContactId(random.nextInt());
	private final ContactId contactId2 = new ContactId(random.nextInt());
	private final ContactId contactId3 = new ContactId(random.nextInt());
	private final long now = System.currentTimeMillis();
	private final long lastAttempt = now - 1234;
	private final long lastSuccess = now - 2345;
	private final int attempts = 123;

	public MailboxSettingsManagerImplTest() {
		pairedSettings = new Settings();
		pairedSettings.put(SETTINGS_KEY_ONION, onion);
		pairedSettings.put(SETTINGS_KEY_TOKEN, token.toString());
		pairedSettings.putIntArray(SETTINGS_KEY_SERVER_SUPPORTS,
				serverSupportsInts);
	}

	@Test
	public void testReturnsNullPropertiesIfSettingsAreEmpty() throws Exception {
		Transaction txn = new Transaction(null, true);
		Settings emptySettings = new Settings();

		context.checking(new Expectations() {{
			oneOf(settingsManager).getSettings(txn, SETTINGS_NAMESPACE);
			will(returnValue(emptySettings));
		}});

		assertNull(manager.getOwnMailboxProperties(txn));
	}

	@Test
	public void testReturnsProperties() throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new Expectations() {{
			oneOf(settingsManager).getSettings(txn, SETTINGS_NAMESPACE);
			will(returnValue(pairedSettings));
		}});

		MailboxProperties properties = manager.getOwnMailboxProperties(txn);
		assertNotNull(properties);
		assertEquals(onion, properties.getOnion());
		assertEquals(token, properties.getAuthToken());
		assertEquals(serverSupports, properties.getServerSupports());
		assertTrue(properties.isOwner());
	}

	@Test
	public void testStoresProperties() throws Exception {
		Transaction txn = new Transaction(null, false);

		manager.registerMailboxHook(hook);

		context.checking(new Expectations() {{
			oneOf(settingsManager).mergeSettings(txn, pairedSettings,
					SETTINGS_NAMESPACE);
			oneOf(hook).mailboxPaired(txn, properties);
		}});

		manager.setOwnMailboxProperties(txn, properties);
	}

	@Test
	public void testRemovesProperties() throws Exception {
		Transaction txn = new Transaction(null, false);
		Settings expectedSettings = new Settings();
		expectedSettings.put(SETTINGS_KEY_ONION, "");
		expectedSettings.put(SETTINGS_KEY_TOKEN, "");
		expectedSettings.put(SETTINGS_KEY_ATTEMPTS, "");
		expectedSettings.put(SETTINGS_KEY_LAST_ATTEMPT, "");
		expectedSettings.put(SETTINGS_KEY_LAST_SUCCESS, "");
		expectedSettings.put(SETTINGS_KEY_SERVER_SUPPORTS, "");

		manager.registerMailboxHook(hook);

		context.checking(new Expectations() {{
			oneOf(settingsManager).mergeSettings(txn, expectedSettings,
					SETTINGS_NAMESPACE);
			oneOf(hook).mailboxUnpaired(txn);
		}});

		manager.removeOwnMailboxProperties(txn);
	}

	@Test
	public void testReturnsDefaultStatusIfSettingsAreEmpty() throws Exception {
		Transaction txn = new Transaction(null, true);
		Settings emptySettings = new Settings();

		context.checking(new Expectations() {{
			oneOf(settingsManager).getSettings(txn, SETTINGS_NAMESPACE);
			will(returnValue(emptySettings));
		}});

		MailboxStatus status = manager.getOwnMailboxStatus(txn);
		assertEquals(-1, status.getTimeOfLastAttempt());
		assertEquals(-1, status.getTimeOfLastSuccess());
		assertEquals(0, status.getAttemptsSinceSuccess());
	}

	@Test
	public void testReturnsStatus() throws Exception {
		Transaction txn = new Transaction(null, true);
		Settings settings = new Settings();
		settings.putLong(SETTINGS_KEY_LAST_ATTEMPT, lastAttempt);
		settings.putLong(SETTINGS_KEY_LAST_SUCCESS, lastSuccess);
		settings.putInt(SETTINGS_KEY_ATTEMPTS, attempts);

		context.checking(new Expectations() {{
			oneOf(settingsManager).getSettings(txn, SETTINGS_NAMESPACE);
			will(returnValue(settings));
		}});

		MailboxStatus status = manager.getOwnMailboxStatus(txn);
		assertEquals(lastAttempt, status.getTimeOfLastAttempt());
		assertEquals(lastSuccess, status.getTimeOfLastSuccess());
		assertEquals(attempts, status.getAttemptsSinceSuccess());
	}

	@Test
	public void testRecordsSuccess() throws Exception {
		Transaction txn = new Transaction(null, false);
		Settings expectedSettings = new Settings();
		expectedSettings.putLong(SETTINGS_KEY_LAST_ATTEMPT, now);
		expectedSettings.putLong(SETTINGS_KEY_LAST_SUCCESS, now);
		expectedSettings.putInt(SETTINGS_KEY_ATTEMPTS, 0);
		expectedSettings.putIntArray(SETTINGS_KEY_SERVER_SUPPORTS,
				serverSupportsInts);

		context.checking(new Expectations() {{
			oneOf(settingsManager).getSettings(txn, SETTINGS_NAMESPACE);
			will(returnValue(pairedSettings));
			oneOf(settingsManager).mergeSettings(txn, expectedSettings,
					SETTINGS_NAMESPACE);
		}});

		manager.recordSuccessfulConnection(txn, now, serverSupports);
		OwnMailboxConnectionStatusEvent e =
				getEvent(txn, OwnMailboxConnectionStatusEvent.class);
		MailboxStatus status = e.getStatus();
		assertEquals(now, status.getTimeOfLastAttempt());
		assertEquals(now, status.getTimeOfLastSuccess());
		assertEquals(0, status.getAttemptsSinceSuccess());
		assertFalse(status.hasProblem(now));
	}

	@Test
	public void testDoesNotRecordSuccessIfNotPaired() throws Exception {
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(settingsManager).getSettings(txn, SETTINGS_NAMESPACE);
			will(returnValue(new Settings()));
		}});

		manager.recordSuccessfulConnection(txn, now, serverSupports);
		assertFalse(hasEvent(txn, OwnMailboxConnectionStatusEvent.class));
	}

	@Test
	public void testRecordsFailureOnFirstAttempt() throws Exception {
		testRecordsFailure(pairedSettings, 0, 0);
	}

	@Test
	public void testRecordsFailureOnLaterAttempt() throws Exception {
		Settings oldSettings = new Settings();
		oldSettings.putAll(pairedSettings);
		oldSettings.putLong(SETTINGS_KEY_LAST_ATTEMPT, lastAttempt);
		oldSettings.putLong(SETTINGS_KEY_LAST_SUCCESS, lastSuccess);
		oldSettings.putInt(SETTINGS_KEY_ATTEMPTS, attempts);
		testRecordsFailure(oldSettings, attempts, lastSuccess);
	}

	private void testRecordsFailure(Settings oldSettings, int oldAttempts,
			long lastSuccess) throws Exception {
		Transaction txn = new Transaction(null, false);
		Settings expectedSettings = new Settings();
		expectedSettings.putLong(SETTINGS_KEY_LAST_ATTEMPT, now);
		expectedSettings.putInt(SETTINGS_KEY_ATTEMPTS, oldAttempts + 1);

		context.checking(new Expectations() {{
			oneOf(settingsManager).getSettings(txn, SETTINGS_NAMESPACE);
			will(returnValue(oldSettings));
			oneOf(settingsManager).mergeSettings(txn, expectedSettings,
					SETTINGS_NAMESPACE);
		}});

		manager.recordFailedConnectionAttempt(txn, now);
		OwnMailboxConnectionStatusEvent e =
				getEvent(txn, OwnMailboxConnectionStatusEvent.class);
		MailboxStatus status = e.getStatus();
		assertEquals(now, status.getTimeOfLastAttempt());
		assertEquals(lastSuccess, status.getTimeOfLastSuccess());
		assertEquals(oldAttempts + 1, status.getAttemptsSinceSuccess());
	}

	@Test
	public void testDoesNotRecordFailureIfNotPaired() throws Exception {
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(settingsManager).getSettings(txn, SETTINGS_NAMESPACE);
			will(returnValue(new Settings()));
		}});

		manager.recordFailedConnectionAttempt(txn, now);
		assertFalse(hasEvent(txn, OwnMailboxConnectionStatusEvent.class));
	}

	@Test
	public void testGettingPendingUploads() throws Exception {
		Transaction txn = new Transaction(null, true);
		Settings settings = new Settings();
		settings.put(String.valueOf(contactId1.getInt()), onion);
		settings.put(String.valueOf(contactId2.getInt()), token.toString());
		settings.put(String.valueOf(contactId3.getInt()), "");

		context.checking(new Expectations() {{
			exactly(4).of(settingsManager)
					.getSettings(txn, SETTINGS_UPLOADS_NAMESPACE);
			will(returnValue(settings));
		}});

		String filename1 = manager.getPendingUpload(txn, contactId1);
		assertEquals(onion, filename1);
		String filename2 = manager.getPendingUpload(txn, contactId2);
		assertNotNull(filename2);
		assertEquals(token.toString(), filename2);
		String filename3 = manager.getPendingUpload(txn, contactId3);
		assertNull(filename3);
		String filename4 =
				manager.getPendingUpload(txn, new ContactId(random.nextInt()));
		assertNull(filename4);
	}

	@Test
	public void testSettingPendingUploads() throws Exception {
		Transaction txn = new Transaction(null, false);

		// setting a pending upload stores expected settings
		Settings expectedSettings1 = new Settings();
		expectedSettings1.put(String.valueOf(contactId1.getInt()), onion);
		context.checking(new Expectations() {{
			oneOf(settingsManager).mergeSettings(txn, expectedSettings1,
					SETTINGS_UPLOADS_NAMESPACE);
		}});
		manager.setPendingUpload(txn, contactId1, onion);

		// nulling a pending upload empties stored settings
		Settings expectedSettings2 = new Settings();
		expectedSettings2.put(String.valueOf(contactId2.getInt()), "");
		context.checking(new Expectations() {{
			oneOf(settingsManager).mergeSettings(txn, expectedSettings2,
					SETTINGS_UPLOADS_NAMESPACE);
		}});
		manager.setPendingUpload(txn, contactId2, null);
	}
}
