package org.briarproject.briar.android.socialbackup.recover;

import android.app.Application;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.widget.Toast;

import com.google.zxing.Result;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.R;
import org.briarproject.briar.android.contact.add.nearby.QrCodeDecoder;
import org.briarproject.briar.android.viewmodel.LiveEvent;
import org.briarproject.briar.android.viewmodel.MutableLiveEvent;
import org.briarproject.briar.api.socialbackup.SocialBackupManager;
import org.briarproject.briar.api.socialbackup.recovery.CustodianTask;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static android.content.Context.WIFI_SERVICE;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;

public class CustodianReturnShardViewModel extends AndroidViewModel
		implements QrCodeDecoder.ResultCallback, CustodianTask.Observer {

	private static final Logger LOG =
			getLogger(CustodianReturnShardViewModel.class.getName());

	private final AndroidExecutor androidExecutor;
	private final Executor ioExecutor;
	private final SocialBackupManager socialBackupManager;
	private final DatabaseComponent db;
	final QrCodeDecoder qrCodeDecoder;
	private boolean qrCodeRead = false;
	private WifiManager wifiManager;
	private final MutableLiveEvent<Boolean > continueClicked = new MutableLiveEvent<>();
	private final MutableLiveEvent<Boolean> showCameraFragment =
			new MutableLiveEvent<>();
	private final MutableLiveEvent<Boolean> successDismissed =
			new MutableLiveEvent<>();
	private final MutableLiveEvent<Boolean> errorTryAgain = new MutableLiveEvent<>();
	private final MutableLiveData<CustodianTask.State> state =
			new MutableLiveData<>();
	private final CustodianTask task;
	private byte[] returnShardPayloadBytes;

	@SuppressWarnings("CharsetObjectCanBeUsed") // Requires minSdkVersion >= 19
	private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");

	@Inject
	public CustodianReturnShardViewModel(
			@NonNull Application app,
			@IoExecutor Executor ioExecutor,
			SocialBackupManager socialBackupManager,
			DatabaseComponent db,
			CustodianTask task,
			AndroidExecutor androidExecutor) {
		super(app);

		this.androidExecutor = androidExecutor;
		this.ioExecutor = ioExecutor;
		this.socialBackupManager = socialBackupManager;
		this.db = db;
		this.task = task;
		qrCodeDecoder = new QrCodeDecoder(androidExecutor, ioExecutor, this);
		wifiManager = (WifiManager) app.getSystemService(WIFI_SERVICE);
	}

	private InetAddress getWifiIpv4Address() {
		if (wifiManager == null) return null;
		// If we're connected to a wifi network, return its address
		WifiInfo info = wifiManager.getConnectionInfo();
		if (info != null && info.getIpAddress() != 0) {
			return intToInetAddress(info.getIpAddress());
		}
		return null;
	}

	// TODO this is not the right place for this
	private InetAddress intToInetAddress(int ip) {
		byte[] ipBytes = new byte[4];
		ipBytes[0] = (byte) (ip & 0xFF);
		ipBytes[1] = (byte) ((ip >> 8) & 0xFF);
		ipBytes[2] = (byte) ((ip >> 16) & 0xFF);
		ipBytes[3] = (byte) ((ip >> 24) & 0xFF);
		try {
			return InetAddress.getByAddress(ipBytes);
		} catch (UnknownHostException e) {
			// Should only be thrown if address has illegal length
			throw new AssertionError(e);
		}
	}

	public void start(ContactId contactId) throws DbException {
		// TODO this should be transactionWithResult
		db.transaction(false, txn -> {
			if (!socialBackupManager.amCustodian(txn, contactId)) {
				throw new DbException();
			}
			returnShardPayloadBytes = socialBackupManager
					.getReturnShardPayloadBytes(txn, contactId);
		});
	}

	@IoExecutor
	@Override
	public void onQrCodeDecoded(Result result) {
		LOG.info("Got result from decoder");
		if (qrCodeRead) return;
		qrCodeRead = true;
		try {
			byte[] payloadBytes = result.getText().getBytes(ISO_8859_1);
			if (LOG.isLoggable(INFO))
				LOG.info("Remote payload is " + payloadBytes.length + " bytes");
			ioExecutor.execute(() -> {
				task.qrCodeDecoded(payloadBytes);
			});
		} catch (IllegalArgumentException e) {
			LOG.log(WARNING, "QR Code Invalid", e);
			androidExecutor.runOnUiThread(() -> Toast.makeText(getApplication(),
					R.string.qr_code_invalid, LENGTH_LONG).show());
			ioExecutor.execute(() -> {
				task.qrCodeDecoded(null);
			});
		}
	}

	public void beginTransfer() throws IOException {
		InetAddress inetAddress = getWifiIpv4Address();
		LOG.info("Client InetAddress: " + inetAddress);
		if (inetAddress == null)
			throw new IOException("Cannot get IP on local wifi");

		task.cancel();
		task.start(this, returnShardPayloadBytes);
		//TODO camera permissions
		showCameraFragment.setEvent(true);
	}
	@UiThread
	public void onContinueClicked() {
		continueClicked.setEvent(true);
//		checkPermissions.setEvent(true);
	}

	@UiThread
	public void onErrorCancelled() {
		errorTryAgain.postEvent(false);
	}

	@UiThread
	public void onErrorTryAgain() {
		errorTryAgain.postEvent(true);
	}

	@UiThread
	public void onSuccessDismissed() {
		successDismissed.setEvent(true);
	}


	QrCodeDecoder getQrCodeDecoder() {
		return qrCodeDecoder;
	}

	LiveEvent<Boolean> getShowCameraFragment() {
		return showCameraFragment;
	}

	LiveEvent<Boolean> getSuccessDismissed() {
		return successDismissed;
	}

	LiveData<CustodianTask.State> getState() {
		return state;
	}

	@Override
	public void onStateChanged(CustodianTask.State state) {
		this.state.postValue(state);
		// Connecting, SendingShard, ReceivingAck, Success, Failure
	}

	public MutableLiveEvent<Boolean> getErrorTryAgain() {
		return errorTryAgain;
	}

	public MutableLiveEvent<Boolean> getContinueClicked() { return continueClicked; }
}
