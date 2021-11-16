package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager;
import org.briarproject.bramble.api.mailbox.MailboxStatus;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;

@Immutable
@NotNullByDefault
class MailboxSettingsManagerImpl implements MailboxSettingsManager {

	// Package access for testing
	static final String SETTINGS_NAMESPACE = "mailbox";
	static final String SETTINGS_KEY_ONION = "onion";
	static final String SETTINGS_KEY_TOKEN = "token";
	static final String SETTINGS_KEY_LAST_ATTEMPT = "lastAttempt";
	static final String SETTINGS_KEY_LAST_SUCCESS = "lastSuccess";
	static final String SETTINGS_KEY_ATTEMPTS = "attempts";

	private final SettingsManager settingsManager;

	@Inject
	MailboxSettingsManagerImpl(SettingsManager settingsManager) {
		this.settingsManager = settingsManager;
	}

	@Override
	public MailboxProperties getOwnMailboxProperties(Transaction txn)
			throws DbException {
		Settings s = settingsManager.getSettings(txn, SETTINGS_NAMESPACE);
		String onion = s.get(SETTINGS_KEY_ONION);
		String token = s.get(SETTINGS_KEY_TOKEN);
		if (isNullOrEmpty(onion) || isNullOrEmpty(token)) return null;
		return new MailboxProperties(onion, token, true);
	}

	@Override
	public void setOwnMailboxProperties(Transaction txn, MailboxProperties p)
			throws DbException {
		Settings s = new Settings();
		s.put(SETTINGS_KEY_ONION, p.getOnionAddress());
		s.put(SETTINGS_KEY_TOKEN, p.getAuthToken());
		settingsManager.mergeSettings(txn, s, SETTINGS_NAMESPACE);
	}

	@Override
	public MailboxStatus getOwnMailboxStatus(Transaction txn)
			throws DbException {
		Settings s = settingsManager.getSettings(txn, SETTINGS_NAMESPACE);
		long lastAttempt = s.getLong(SETTINGS_KEY_LAST_ATTEMPT, -1);
		long lastSuccess = s.getLong(SETTINGS_KEY_LAST_SUCCESS, -1);
		int attempts = s.getInt(SETTINGS_KEY_ATTEMPTS, 0);
		return new MailboxStatus(lastAttempt, lastSuccess, attempts);
	}

	@Override
	public void recordSuccessfulConnection(Transaction txn, long now)
			throws DbException {
		Settings s = new Settings();
		s.putLong(SETTINGS_KEY_LAST_ATTEMPT, now);
		s.putLong(SETTINGS_KEY_LAST_SUCCESS, now);
		s.putInt(SETTINGS_KEY_ATTEMPTS, 0);
		settingsManager.mergeSettings(txn, s, SETTINGS_NAMESPACE);
	}

	@Override
	public void recordFailedConnectionAttempt(Transaction txn, long now)
			throws DbException {
		Settings oldSettings =
				settingsManager.getSettings(txn, SETTINGS_NAMESPACE);
		int attempts = oldSettings.getInt(SETTINGS_KEY_ATTEMPTS, 0);
		Settings newSettings = new Settings();
		newSettings.putLong(SETTINGS_KEY_LAST_ATTEMPT, now);
		newSettings.putInt(SETTINGS_KEY_ATTEMPTS, attempts + 1);
		settingsManager.mergeSettings(txn, newSettings, SETTINGS_NAMESPACE);
	}
}
