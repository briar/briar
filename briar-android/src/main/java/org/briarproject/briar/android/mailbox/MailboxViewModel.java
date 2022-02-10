package org.briarproject.briar.android.mailbox;

import android.app.Application;

import com.google.zxing.Result;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.mailbox.MailboxAuthToken;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager;
import org.briarproject.bramble.api.mailbox.MailboxStatus;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.android.mailbox.MailboxState.NotSetup;
import org.briarproject.briar.android.qrcode.QrCodeDecoder;
import org.briarproject.briar.android.viewmodel.DbViewModel;
import org.briarproject.briar.android.viewmodel.LiveEvent;
import org.briarproject.briar.android.viewmodel.MutableLiveEvent;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.UiThread;

import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;

@NotNullByDefault
class MailboxViewModel extends DbViewModel
		implements QrCodeDecoder.ResultCallback {

	private static final Logger LOG =
			getLogger(MailboxViewModel.class.getName());

	@SuppressWarnings("CharsetObjectCanBeUsed") // Requires minSdkVersion >= 19
	private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");
	private static final int VERSION_REQUIRED = 32;

	private final CryptoComponent crypto;
	private final QrCodeDecoder qrCodeDecoder;
	private final PluginManager pluginManager;
	private final MailboxSettingsManager mailboxSettingsManager;

	private final MutableLiveEvent<MailboxState> state =
			new MutableLiveEvent<>();

	@Inject
	MailboxViewModel(
			Application app,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			TransactionManager db,
			AndroidExecutor androidExecutor,
			@IoExecutor Executor ioExecutor,
			CryptoComponent crypto,
			PluginManager pluginManager,
			MailboxSettingsManager mailboxSettingsManager) {
		super(app, dbExecutor, lifecycleManager, db, androidExecutor);
		this.crypto = crypto;
		this.pluginManager = pluginManager;
		this.mailboxSettingsManager = mailboxSettingsManager;
		qrCodeDecoder = new QrCodeDecoder(androidExecutor, ioExecutor, this);
		checkIfSetup();
	}

	@UiThread
	private void checkIfSetup() {
		runOnDbThread(true, txn -> {
			MailboxProperties props =
					mailboxSettingsManager.getOwnMailboxProperties(txn);
			if (props == null) state.postEvent(new NotSetup());
			else {
				MailboxStatus mailboxStatus =
						mailboxSettingsManager.getOwnMailboxStatus(txn);
				state.postEvent(new MailboxState.IsSetup(mailboxStatus));
			}
		}, this::handleException);
	}

	@UiThread
	void onScanButtonClicked() {
		if (isTorActive()) {
			state.setEvent(new MailboxState.ScanningQrCode());
		} else {
			state.setEvent(new MailboxState.OfflineInSetup());
		}
	}

	@Override
	@IoExecutor
	public void onQrCodeDecoded(Result result) {
		LOG.info("Got result from decoder");
		MailboxProperties properties;
		try {
			properties = decodeQrCode(result.getText());
		} catch (FormatException e) {
			state.postEvent(new MailboxState.QrCodeWrong());
			return;
		}
		onMailboxPropertiesReceived(properties);
	}

	@IoExecutor
	// TODO move this into core #2168
	private MailboxProperties decodeQrCode(String payload)
			throws FormatException {
		byte[] bytes = payload.getBytes(ISO_8859_1);
		if (bytes.length != 65) {
			if (LOG.isLoggable(WARNING)) {
				LOG.warning("QR code length is not 65: " + bytes.length);
			}
			throw new FormatException();
		}
		int version = bytes[0] & 0xFF;
		if (version != VERSION_REQUIRED) {
			if (LOG.isLoggable(WARNING)) {
				LOG.warning("QR code has not version " + VERSION_REQUIRED +
						": " + version);
			}
			throw new FormatException();
		}
		LOG.info("QR code is valid");
		byte[] onionPubKey = Arrays.copyOfRange(bytes, 1, 33);
		String onionAddress = crypto.encodeOnionAddress(onionPubKey);
		byte[] tokenBytes = Arrays.copyOfRange(bytes, 33, 65);
		MailboxAuthToken setupToken = new MailboxAuthToken(tokenBytes);
		return new MailboxProperties(onionAddress, setupToken, true);
	}

	private void onMailboxPropertiesReceived(MailboxProperties properties) {
		if (isTorActive()) {
			// TODO pass props to core #2168
			state.postEvent(new MailboxState.SettingUp());
		} else {
			state.postEvent(new MailboxState.OfflineInSetup(properties));
		}
	}

	// TODO ideally also move this into core #2168
	private boolean isTorActive() {
		Plugin plugin = pluginManager.getPlugin(TorConstants.ID);
		return plugin != null && plugin.getState() == ACTIVE;
	}

	@UiThread
	void tryAgainWhenOffline() {
		MailboxState.OfflineInSetup offline =
				(MailboxState.OfflineInSetup) requireNonNull(
						state.getLastValue());
		if (offline.mailboxProperties == null) {
			onScanButtonClicked();
		} else {
			onMailboxPropertiesReceived(offline.mailboxProperties);
		}
	}

	@UiThread
	QrCodeDecoder getQrCodeDecoder() {
		return qrCodeDecoder;
	}

	@UiThread
	LiveEvent<MailboxState> getState() {
		return state;
	}
}
