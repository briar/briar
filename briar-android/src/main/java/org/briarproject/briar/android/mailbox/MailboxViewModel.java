package org.briarproject.briar.android.mailbox;

import android.app.Application;

import com.google.zxing.Result;

import org.briarproject.bramble.api.Consumer;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.mailbox.MailboxManager;
import org.briarproject.bramble.api.mailbox.MailboxPairingState;
import org.briarproject.bramble.api.mailbox.MailboxPairingTask;
import org.briarproject.bramble.api.mailbox.MailboxStatus;
import org.briarproject.bramble.api.mailbox.OwnMailboxConnectionStatusEvent;
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
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;

@NotNullByDefault
class MailboxViewModel extends DbViewModel
		implements QrCodeDecoder.ResultCallback, Consumer<MailboxPairingState>,
		EventListener {

	private static final Logger LOG =
			getLogger(MailboxViewModel.class.getName());

	private final EventBus eventBus;
	private final QrCodeDecoder qrCodeDecoder;
	private final PluginManager pluginManager;
	private final MailboxManager mailboxManager;

	private final MutableLiveEvent<MailboxState> pairingState =
			new MutableLiveEvent<>();
	private final MutableLiveData<MailboxStatus> status =
			new MutableLiveData<>();
	@Nullable
	private MailboxPairingTask pairingTask = null;

	@Inject
	MailboxViewModel(
			Application app,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			TransactionManager db,
			AndroidExecutor androidExecutor,
			EventBus eventBus,
			@IoExecutor Executor ioExecutor,
			PluginManager pluginManager,
			MailboxManager mailboxManager) {
		super(app, dbExecutor, lifecycleManager, db, androidExecutor);
		this.eventBus = eventBus;
		this.pluginManager = pluginManager;
		this.mailboxManager = mailboxManager;
		qrCodeDecoder = new QrCodeDecoder(androidExecutor, ioExecutor, this);
		eventBus.addListener(this);
		checkIfSetup();
	}

	@Override
	protected void onCleared() {
		super.onCleared();
		eventBus.removeListener(this);
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
					pairingState.postEvent(new MailboxState.IsPaired());
					status.postValue(mailboxStatus);
				} else {
					pairingState.postEvent(new NotSetup());
				}
			}, this::handleException);
		} else {
			task.addObserver(this);
			pairingTask = task;
		}
	}

	@UiThread
	@Override
	public void eventOccurred(Event e) {
		if (e instanceof OwnMailboxConnectionStatusEvent) {
			MailboxStatus status =
					((OwnMailboxConnectionStatusEvent) e).getStatus();
			this.status.setValue(status);
		}
	}

	@UiThread
	void onScanButtonClicked() {
		if (isTorActive()) {
			pairingState.setEvent(new MailboxState.ScanningQrCode());
		} else {
			pairingState.setEvent(new MailboxState.OfflineWhenPairing());
		}
	}

	@UiThread
	void onCameraError() {
		pairingState.setEvent(new MailboxState.CameraError());
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
			pairingState.postEvent(new MailboxState.OfflineWhenPairing());
		}
	}

	@UiThread
	@Override
	public void accept(MailboxPairingState mailboxPairingState) {
		if (LOG.isLoggable(INFO)) {
			LOG.info("New pairing state: " +
					mailboxPairingState.getClass().getSimpleName());
		}
		pairingState.setEvent(new MailboxState.Pairing(mailboxPairingState));
	}

	private boolean isTorActive() {
		Plugin plugin = pluginManager.getPlugin(TorConstants.ID);
		return plugin != null && plugin.getState() == ACTIVE;
	}

	@UiThread
	void showDownloadFragment() {
		pairingState.setEvent(new MailboxState.ShowDownload());
	}

	@UiThread
	QrCodeDecoder getQrCodeDecoder() {
		return qrCodeDecoder;
	}

	LiveData<Boolean> checkConnection() {
		MutableLiveData<Boolean> liveData = new MutableLiveData<>();
		mailboxManager.checkConnection(result ->
				onConnectionCheckFinished(liveData, result));
		return liveData;
	}

	@IoExecutor
	private void onConnectionCheckFinished(MutableLiveData<Boolean> liveData,
			boolean success) {
		if (LOG.isLoggable(INFO)) {
			LOG.info("Got result from connection check: " + success);
		}
		liveData.postValue(success);
	}

	@UiThread
	LiveEvent<MailboxState> getPairingState() {
		return pairingState;
	}

	@UiThread
	LiveData<MailboxStatus> getStatus() {
		return status;
	}
}
