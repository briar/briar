package org.briarproject.briar.android.socialbackup.recover;

import android.app.Application;
import android.widget.Toast;

import com.google.zxing.Result;

import org.briarproject.bramble.api.UnsupportedVersionException;
import org.briarproject.bramble.api.keyagreement.Payload;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.R;
import org.briarproject.briar.android.contact.add.nearby.QrCodeDecoder;
import org.briarproject.briar.android.viewmodel.LiveEvent;
import org.briarproject.briar.android.viewmodel.MutableLiveEvent;
import org.briarproject.briar.api.socialbackup.recovery.CustodianTask;
import org.briarproject.briar.api.socialbackup.recovery.SecretOwnerTask;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.cert.CertPathValidatorException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static android.widget.Toast.LENGTH_LONG;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;

public class CustodianReturnShardViewModel extends AndroidViewModel
		implements QrCodeDecoder.ResultCallback, CustodianTask.Observer {

	private static final Logger LOG =
			getLogger(CustodianReturnShardViewModel.class.getName());

	private final AndroidExecutor androidExecutor;
	private final Executor ioExecutor;
	private boolean wasContinueClicked = false;
	private boolean qrCodeRead = false;
	private final MutableLiveEvent<Boolean> showCameraFragment =
			new MutableLiveEvent<>();
	private final MutableLiveEvent<Boolean> successDismissed =
			new MutableLiveEvent<>();
	private final MutableLiveData<CustodianTask.State> state =
			new MutableLiveData<>();
	final QrCodeDecoder qrCodeDecoder;
	private final CustodianTask task;

	@SuppressWarnings("CharsetObjectCanBeUsed") // Requires minSdkVersion >= 19
	private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");

	@Inject
	public CustodianReturnShardViewModel(
			@NonNull Application application,
			@IoExecutor Executor ioExecutor,
			CustodianTask task,
			AndroidExecutor androidExecutor) {
		super(application);

		this.androidExecutor = androidExecutor;
		this.ioExecutor = ioExecutor;
		this.task = task;
		qrCodeDecoder = new QrCodeDecoder(androidExecutor, ioExecutor, this);
		task.cancel();
		task.start(this, "replace this with the shard".getBytes());
	}

	@IoExecutor
	@Override
	public void onQrCodeDecoded(Result result) {
		LOG.info("Got result from decoder");
		if (qrCodeRead) return;
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

	@UiThread
	public void onContinueClicked() {
		wasContinueClicked = true;
//		checkPermissions.setEvent(true);
		showCameraFragment.setEvent(true);
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
        if (state instanceof CustodianTask.State.SendingShard) {
            qrCodeRead = true;
        }
	}
}
