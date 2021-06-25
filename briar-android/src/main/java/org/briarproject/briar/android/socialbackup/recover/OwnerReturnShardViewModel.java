package org.briarproject.briar.android.socialbackup.recover;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.DisplayMetrics;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.android.contact.add.nearby.QrCodeUtils;
import org.briarproject.briar.android.viewmodel.LiveEvent;
import org.briarproject.briar.android.viewmodel.MutableLiveEvent;
import org.briarproject.briar.api.socialbackup.MessageEncoder;
import org.briarproject.briar.api.socialbackup.ReturnShardPayload;
import org.briarproject.briar.api.socialbackup.recovery.RestoreAccount;
import org.briarproject.briar.api.socialbackup.recovery.SecretOwnerTask;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.UiThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static android.content.Context.WIFI_SERVICE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;

@NotNullByDefault
class OwnerReturnShardViewModel extends AndroidViewModel
		implements SecretOwnerTask.Observer {

	private static final Logger LOG =
			getLogger(OwnerReturnShardViewModel.class.getName());

	@SuppressWarnings("CharsetObjectCanBeUsed") // Requires minSdkVersion >= 19
	private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");

	private final AndroidExecutor androidExecutor;
	private final Executor ioExecutor;
	private final SecretOwnerTask task;
	private final RestoreAccount restoreAccount;
	private final SharedPreferences prefs;

	private final MutableLiveEvent<Boolean> errorTryAgain =
			new MutableLiveEvent<>();
	private final MutableLiveEvent<Boolean> showQrCodeFragment =
			new MutableLiveEvent<>();
	private final MutableLiveEvent<Boolean> successDismissed = new MutableLiveEvent<>();
	private final MutableLiveData<SecretOwnerTask.State> state =
			new MutableLiveData<>();
	private final MutableLiveEvent<Boolean> startClicked =
			new MutableLiveEvent<>();
	private boolean wasContinueClicked = false;
	private boolean isActivityResumed = false;
	private Bitmap qrCodeBitmap;
	private WifiManager wifiManager;
	private SecretKey secretKey;
	private final MessageEncoder messageEncoder;

	@Inject
	OwnerReturnShardViewModel(Application app,
			AndroidExecutor androidExecutor,
			SecretOwnerTask task,
			RestoreAccount restoreAccount,
			@IoExecutor Executor ioExecutor,
			MessageEncoder messageEncoder) {
		super(app);
		this.androidExecutor = androidExecutor;
		this.ioExecutor = ioExecutor;
		this.restoreAccount = restoreAccount;
		this.messageEncoder = messageEncoder;
		this.task = task;
		this.prefs = app.getSharedPreferences("account-recovery",
				Context.MODE_PRIVATE);
		restoreAccount.restoreFromPrevious(prefs.getStringSet("Recover", new HashSet<>()));

		wifiManager = (WifiManager) app.getSystemService(WIFI_SERVICE);

//		IntentFilter filter = new IntentFilter(ACTION_SCAN_MODE_CHANGED);
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

	@Override
	protected void onCleared() {
		super.onCleared();
		stopListening();
	}

	@UiThread
	void onStartClicked() {
       startClicked.setEvent(true);
	}

	@UiThread
	void onContinueClicked() {
		wasContinueClicked = true;
		startShardReturn();
	}

	@UiThread
	void onSuccessDismissed() {
	   successDismissed.setEvent(true);
	}

	@UiThread
	void startShardReturn() {
		// If we return to the intro fragment, the continue button needs to be
		// clicked again before showing the QR code fragment
		wasContinueClicked = false;
		// If we return to the intro fragment, we may need to enable wifi and
//		hasEnabledWifi = false;
		showQrCodeFragment.setEvent(true);
	}

	@UiThread
	public void startListening() {
		ioExecutor.execute(() -> {
			task.start(this, getWifiIpv4Address());
		});
//		KeyAgreementTask oldTask = task;
//		KeyAgreementTask newTask = keyAgreementTaskProvider.get();
//		task = newTask;
//		ioExecutor.execute(() -> {
//			if (oldTask != null) oldTask.stopListening();
//			newTask.listen();
//		});
	}

	@UiThread
	private void stopListening() {
		ioExecutor.execute(() -> {
			task.cancel();
		});
	}

	@UiThread
	public void onErrorTryAgain() {
	     errorTryAgain.setEvent(true);
	}

	/**
	 * Set to true in onPostResume() and false in onPause(). This prevents the
	 * QR code fragment from being shown if onRequestPermissionsResult() is
	 * called while the activity is paused, which could cause a crash due to
	 * https://issuetracker.google.com/issues/37067655.
	 * TODO check if this is still happening with new permission requesting
	 */
	void setIsActivityResumed(boolean resumed) {
		isActivityResumed = resumed;
		// Workaround for
		// https://code.google.com/p/android/issues/detail?id=190966
//		showQrCodeFragmentIfAllowed();
	}

	LiveEvent<Boolean> getShowQrCodeFragment() {
		return showQrCodeFragment;
	}

	LiveEvent<Boolean> getStartClicked() {
		return startClicked;
	}

	LiveData<SecretOwnerTask.State> getState() {
		return state;
	}

	public Bitmap getQrCodeBitmap() {
		LOG.info("getting qrCodeBitmap");
		return qrCodeBitmap;
	}

	public int getNumberOfShards() {
		return restoreAccount.getNumberOfShards();
	}

	@Override
	public void onStateChanged(SecretOwnerTask.State state) {
		if (state instanceof SecretOwnerTask.State.Listening) {
			DisplayMetrics dm =
					getApplication().getResources().getDisplayMetrics();
			ioExecutor.execute(() -> {
				byte[] payloadBytes = ((SecretOwnerTask.State.Listening) state)
						.getLocalPayload();
				if (LOG.isLoggable(INFO)) {
					LOG.info("Local QR code payload is " + payloadBytes.length
							+ " bytes");
				}
				// Use ISO 8859-1 to encode bytes directly as a string
				String content = new String(payloadBytes, ISO_8859_1);
				qrCodeBitmap = QrCodeUtils.createQrCode(dm, content);
				this.state.postValue(state);
			});
		} else if (state instanceof SecretOwnerTask.State.Success) {
//			startClicked.setEvent(true);
			this.state.postValue(state);
			// TODO do same for failure
		}  else {
			this.state.postValue(state);
		}
	}

	public RestoreAccount.AddReturnShardPayloadResult addToShardSet(ReturnShardPayload toAdd) {
		RestoreAccount.AddReturnShardPayloadResult result = restoreAccount.addReturnShardPayload(toAdd);
		if (result == RestoreAccount.AddReturnShardPayloadResult.OK) {
			prefs.edit().putStringSet("recovered", restoreAccount.getEncodedShards()).apply();
		}
		return result;
	}

	public boolean canRecover() {
		return restoreAccount.canRecover();
	}

	public int recover() throws FormatException, GeneralSecurityException {
		return restoreAccount.recover();
	}

	public MutableLiveEvent<Boolean> getSuccessDismissed() {
		return successDismissed;
	}

	public MutableLiveEvent<Boolean> getErrorTryAgain() {
		return errorTryAgain;
	}
}
