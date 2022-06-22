package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.mailbox.InvalidMailboxIdException;
import org.briarproject.bramble.api.mailbox.MailboxAuthToken;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager;
import org.briarproject.bramble.api.mailbox.MailboxStatus;
import org.briarproject.bramble.api.mailbox.MailboxVersion;
import org.briarproject.bramble.api.mailbox.event.MailboxProblemEvent;
import org.briarproject.bramble.api.mailbox.event.OwnMailboxConnectionStatusEvent;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.util.Collections.emptyList;
import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;

@ThreadSafe
@NotNullByDefault
class MailboxSettingsManagerImpl implements MailboxSettingsManager {

	// Package access for testing
	static final String SETTINGS_NAMESPACE = "mailbox";
	// TODO: This currently stores the base URL, not the 56-char onion address
	static final String SETTINGS_KEY_ONION = "onion";
	static final String SETTINGS_KEY_TOKEN = "token";
	static final String SETTINGS_KEY_SERVER_SUPPORTS = "serverSupports";
	static final String SETTINGS_KEY_LAST_ATTEMPT = "lastAttempt";
	static final String SETTINGS_KEY_LAST_SUCCESS = "lastSuccess";
	static final String SETTINGS_KEY_ATTEMPTS = "attempts";
	static final String SETTINGS_UPLOADS_NAMESPACE = "mailbox-uploads";

	private final SettingsManager settingsManager;
	private final List<MailboxHook> hooks = new CopyOnWriteArrayList<>();

	@Inject
	MailboxSettingsManagerImpl(SettingsManager settingsManager) {
		this.settingsManager = settingsManager;
	}

	@Override
	public void registerMailboxHook(MailboxHook hook) {
		hooks.add(hook);
	}

	@Override
	public MailboxProperties getOwnMailboxProperties(Transaction txn)
			throws DbException {
		Settings s = settingsManager.getSettings(txn, SETTINGS_NAMESPACE);
		String onion = s.get(SETTINGS_KEY_ONION);
		String token = s.get(SETTINGS_KEY_TOKEN);
		if (isNullOrEmpty(onion) || isNullOrEmpty(token)) return null;
		List<MailboxVersion> serverSupports = parseServerSupports(s);
		try {
			MailboxAuthToken tokenId = MailboxAuthToken.fromString(token);
			return new MailboxProperties(onion, tokenId, serverSupports);
		} catch (InvalidMailboxIdException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void setOwnMailboxProperties(Transaction txn, MailboxProperties p)
			throws DbException {
		Settings s = new Settings();
		s.put(SETTINGS_KEY_ONION, p.getBaseUrl());
		s.put(SETTINGS_KEY_TOKEN, p.getAuthToken().toString());
		List<MailboxVersion> serverSupports = p.getServerSupports();
		encodeServerSupports(serverSupports, s);
		settingsManager.mergeSettings(txn, s, SETTINGS_NAMESPACE);
		for (MailboxHook hook : hooks) {
			hook.mailboxPaired(txn, p.getOnion(), p.getServerSupports());
		}
	}

	@Override
	public void removeOwnMailboxProperties(Transaction txn) throws DbException {
		Settings s = new Settings();
		s.put(SETTINGS_KEY_ONION, "");
		s.put(SETTINGS_KEY_TOKEN, "");
		settingsManager.mergeSettings(txn, s, SETTINGS_NAMESPACE);
		for (MailboxHook hook : hooks) {
			hook.mailboxUnpaired(txn);
		}
	}

	@Override
	public MailboxStatus getOwnMailboxStatus(Transaction txn)
			throws DbException {
		Settings s = settingsManager.getSettings(txn, SETTINGS_NAMESPACE);
		long lastAttempt = s.getLong(SETTINGS_KEY_LAST_ATTEMPT, -1);
		long lastSuccess = s.getLong(SETTINGS_KEY_LAST_SUCCESS, -1);
		int attempts = s.getInt(SETTINGS_KEY_ATTEMPTS, 0);
		List<MailboxVersion> serverSupports;
		try {
			serverSupports = parseServerSupports(s);
		} catch (DbException e) {
			serverSupports = emptyList();
		}
		return new MailboxStatus(lastAttempt, lastSuccess, attempts,
				serverSupports);
	}

	@Override
	public void recordSuccessfulConnection(Transaction txn, long now)
			throws DbException {
		recordSuccessfulConnection(txn, now, null);
	}

	@Override
	public void recordSuccessfulConnection(Transaction txn, long now,
			@Nullable List<MailboxVersion> versions) throws DbException {
		Settings s = new Settings();
		// fetch version that the server supports first
		List<MailboxVersion> serverSupports;
		if (versions == null) {
			Settings oldSettings =
					settingsManager.getSettings(txn, SETTINGS_NAMESPACE);
			serverSupports = parseServerSupports(oldSettings);
		} else {
			serverSupports = versions;
			// store new versions
			encodeServerSupports(serverSupports, s);
		}
		// now record the successful connection
		s.putLong(SETTINGS_KEY_LAST_ATTEMPT, now);
		s.putLong(SETTINGS_KEY_LAST_SUCCESS, now);
		s.putInt(SETTINGS_KEY_ATTEMPTS, 0);
		settingsManager.mergeSettings(txn, s, SETTINGS_NAMESPACE);
		// broadcast status event
		MailboxStatus status = new MailboxStatus(now, now, 0, serverSupports);
		txn.attach(new OwnMailboxConnectionStatusEvent(status));
	}

	@Override
	public void recordFailedConnectionAttempt(Transaction txn, long now)
			throws DbException {
		Settings oldSettings =
				settingsManager.getSettings(txn, SETTINGS_NAMESPACE);
		int newAttempts = 1 + oldSettings.getInt(SETTINGS_KEY_ATTEMPTS, 0);
		long lastSuccess = oldSettings.getLong(SETTINGS_KEY_LAST_SUCCESS, 0);
		Settings newSettings = new Settings();
		newSettings.putLong(SETTINGS_KEY_LAST_ATTEMPT, now);
		newSettings.putInt(SETTINGS_KEY_ATTEMPTS, newAttempts);
		settingsManager.mergeSettings(txn, newSettings, SETTINGS_NAMESPACE);
		List<MailboxVersion> serverSupports = parseServerSupports(oldSettings);
		MailboxStatus status = new MailboxStatus(now, lastSuccess, newAttempts,
				serverSupports);
		txn.attach(new OwnMailboxConnectionStatusEvent(status));
		if (status.hasProblem(now)) txn.attach(new MailboxProblemEvent());
	}

	@Override
	public void setPendingUpload(Transaction txn, ContactId id,
			@Nullable String filename) throws DbException {
		Settings s = new Settings();
		String value = filename == null ? "" : filename;
		s.put(String.valueOf(id.getInt()), value);
		settingsManager.mergeSettings(txn, s, SETTINGS_UPLOADS_NAMESPACE);
	}

	@Nullable
	@Override
	public String getPendingUpload(Transaction txn, ContactId id)
			throws DbException {
		Settings s =
				settingsManager.getSettings(txn, SETTINGS_UPLOADS_NAMESPACE);
		String filename = s.get(String.valueOf(id.getInt()));
		if (isNullOrEmpty(filename)) return null;
		return filename;
	}

	private void encodeServerSupports(List<MailboxVersion> serverSupports,
			Settings s) {
		int[] ints = new int[serverSupports.size() * 2];
		int i = 0;
		for (MailboxVersion v : serverSupports) {
			ints[i++] = v.getMajor();
			ints[i++] = v.getMinor();
		}
		s.putIntArray(SETTINGS_KEY_SERVER_SUPPORTS, ints);
	}

	private List<MailboxVersion> parseServerSupports(Settings s)
			throws DbException {
		if (!s.containsKey(SETTINGS_KEY_SERVER_SUPPORTS)) return emptyList();
		int[] ints = s.getIntArray(SETTINGS_KEY_SERVER_SUPPORTS);
		// We know we were paired, so we must have proper serverSupports
		if (ints == null || ints.length == 0 || ints.length % 2 != 0) {
			throw new DbException();
		}
		List<MailboxVersion> serverSupports = new ArrayList<>();
		for (int i = 0; i < ints.length - 1; i += 2) {
			serverSupports.add(new MailboxVersion(ints[i], ints[i + 1]));
		}
		return serverSupports;
	}
}
