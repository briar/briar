package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Test;

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

	@Test
	public void testReturnsNullIfSettingsAreEmpty() throws Exception {
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
}
