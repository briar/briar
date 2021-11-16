package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager;
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
}
