package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager;
import org.briarproject.bramble.api.mailbox.MailboxStatus;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Test;

import static org.briarproject.bramble.mailbox.MailboxSettingsManagerImpl.SETTINGS_KEY_ATTEMPTS;
import static org.briarproject.bramble.mailbox.MailboxSettingsManagerImpl.SETTINGS_KEY_LAST_ATTEMPT;
import static org.briarproject.bramble.mailbox.MailboxSettingsManagerImpl.SETTINGS_KEY_LAST_SUCCESS;
import static org.briarproject.bramble.mailbox.MailboxSettingsManagerImpl.SETTINGS_KEY_ONION;
import static org.briarproject.bramble.mailbox.MailboxSettingsManagerImpl.SETTINGS_KEY_TOKEN;
import static org.briarproject.bramble.mailbox.MailboxSettingsManagerImpl.SETTINGS_NAMESPACE;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MailboxSettingsManagerImplTest extends BrambleMockTestCase {

	private final SettingsManager settingsManager =
			context.mock(SettingsManager.class);

	private final MailboxSettingsManager manager =
			new MailboxSettingsManagerImpl(settingsManager);
	private final String onion = getRandomString(64);
	private final String token = getRandomString(64);
	private final long now = System.currentTimeMillis();
	private final long lastAttempt = now - 1234;
	private final long lastSuccess = now - 2345;
	private final int attempts = 123;

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
		Settings settings = new Settings();
		settings.put(SETTINGS_KEY_ONION, onion);
		settings.put(SETTINGS_KEY_TOKEN, token);

		context.checking(new Expectations() {{
			oneOf(settingsManager).getSettings(txn, SETTINGS_NAMESPACE);
			will(returnValue(settings));
		}});

		MailboxProperties properties = manager.getOwnMailboxProperties(txn);
		assertNotNull(properties);
		assertEquals(onion, properties.getOnionAddress());
		assertEquals(token, properties.getAuthToken());
		assertTrue(properties.isOwner());
	}

	@Test
	public void testStoresProperties() throws Exception {
		Transaction txn = new Transaction(null, false);
		Settings expectedSettings = new Settings();
		expectedSettings.put(SETTINGS_KEY_ONION, onion);
		expectedSettings.put(SETTINGS_KEY_TOKEN, token);
		MailboxProperties properties =
				new MailboxProperties(onion, token, true);

		context.checking(new Expectations() {{
			oneOf(settingsManager).mergeSettings(txn, expectedSettings,
					SETTINGS_NAMESPACE);
		}});

		manager.setOwnMailboxProperties(txn, properties);
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

		context.checking(new Expectations() {{
			oneOf(settingsManager).mergeSettings(txn, expectedSettings,
					SETTINGS_NAMESPACE);
		}});

		manager.recordSuccessfulConnection(txn, now);
	}

	@Test
	public void testRecordsFailureOnFirstAttempt() throws Exception {
		testRecordsFailure(new Settings(), 0);
	}

	@Test
	public void testRecordsFailureOnLaterAttempt() throws Exception {
		Settings oldSettings = new Settings();
		oldSettings.putLong(SETTINGS_KEY_LAST_ATTEMPT, lastAttempt);
		oldSettings.putLong(SETTINGS_KEY_LAST_SUCCESS, lastSuccess);
		oldSettings.putInt(SETTINGS_KEY_ATTEMPTS, attempts);
		testRecordsFailure(oldSettings, attempts);
	}

	private void testRecordsFailure(Settings oldSettings, int oldAttempts)
			throws Exception {
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
	}
}
