package org.briarproject.briar.android.mailbox;

import android.app.Application;

import com.google.zxing.Result;

import org.briarproject.bramble.api.Consumer;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.mailbox.MailboxManager;
import org.briarproject.bramble.api.mailbox.MailboxPairingState;
import org.briarproject.bramble.api.mailbox.MailboxPairingTask;
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

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;

@NotNullByDefault
class MailboxViewModel extends DbViewModel
		implements QrCodeDecoder.ResultCallback, Consumer<MailboxPairingState> {

	private static final Logger LOG =
			getLogger(MailboxViewModel.class.getName());

	private final QrCodeDecoder qrCodeDecoder;
	private final PluginManager pluginManager;
	private final MailboxManager mailboxManager;

	private final MutableLiveEvent<MailboxState> state =
			new MutableLiveEvent<>();
	@Nullable
	private MailboxPairingTask pairingTask = null;

	@Inject
	MailboxViewModel(
			Application app,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			TransactionManager db,
			AndroidExecutor androidExecutor,
			@IoExecutor Executor ioExecutor,
			PluginManager pluginManager,
			MailboxManager mailboxManager) {
		super(app, dbExecutor, lifecycleManager, db, androidExecutor);
		this.pluginManager = pluginManager;
		this.mailboxManager = mailboxManager;
		qrCodeDecoder = new QrCodeDecoder(androidExecutor, ioExecutor, this);
		checkIfSetup();
	}

	@Override
	protected void onCleared() {
		super.onCleared();
		MailboxPairingTask task = pairingTask;
		if (task != null) {
			task.removeObserver(this);
			pairingTask = null;
		}
	}

	@UiThread
	private void checkIfSetup() {
		MailboxPairingTask task = mailboxManager.getCurrentPairingTask();
		if (task == null) {
			runOnDbThread(true, txn -> {
				boolean isPaired = mailboxManager.isPaired(txn);
				if (isPaired) {
					MailboxStatus mailboxStatus =
							mailboxManager.getMailboxStatus(txn);
					state.postEvent(new MailboxState.IsPaired(mailboxStatus));
				} else {
					state.postEvent(new NotSetup());
				}
			}, this::handleException);
		} else {
			task.addObserver(this);
			pairingTask = task;
		}
	}

	@UiThread
	void onScanButtonClicked() {
		if (isTorActive()) {
			state.setEvent(new MailboxState.ScanningQrCode());
		} else {
			state.setEvent(new MailboxState.OfflineWhenPairing());
		}
	}

	@Override
	@IoExecutor
	public void onQrCodeDecoded(Result result) {
		LOG.info("Got result from decoder");
		onQrCodePayloadReceived(result.getText());
	}

	@AnyThread
	private void onQrCodePayloadReceived(String qrCodePayload) {
		if (isTorActive()) {
			pairingTask = mailboxManager.startPairingTask(qrCodePayload);
			pairingTask.addObserver(this);
		} else {
			state.postEvent(new MailboxState.OfflineWhenPairing(qrCodePayload));
		}
	}

	@UiThread
	@Override
	public void accept(MailboxPairingState mailboxPairingState) {
		if (LOG.isLoggable(INFO)) {
			LOG.info("New pairing state: " +
					mailboxPairingState.getClass().getSimpleName());
		}
		state.setEvent(new MailboxState.Pairing(mailboxPairingState));
	}

	private boolean isTorActive() {
		Plugin plugin = pluginManager.getPlugin(TorConstants.ID);
		return plugin != null && plugin.getState() == ACTIVE;
	}

	@UiThread
	void tryAgainWhenOffline() {
		MailboxState.OfflineWhenPairing offline =
				(MailboxState.OfflineWhenPairing) requireNonNull(
						state.getLastValue());
		if (offline.qrCodePayload == null) {
			onScanButtonClicked();
		} else {
			onQrCodePayloadReceived(offline.qrCodePayload);
		}
	}

	@UiThread
	void tryAgainAfterError() {
		MailboxState.Pairing pairing = (MailboxState.Pairing)
				requireNonNull(state.getLastValue());
		if (pairing.getQrCodePayload() == null) {
			onScanButtonClicked();
		} else {
			onQrCodePayloadReceived(pairing.getQrCodePayload());
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
