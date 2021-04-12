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
		implements QrCodeDecoder.ResultCallback {

	private static final Logger LOG =
			getLogger(CustodianReturnShardViewModel.class.getName());

	private final AndroidExecutor androidExecutor;
	private final Executor ioExecutor;
	private boolean wasContinueClicked = false;
	private final MutableLiveEvent<Boolean> showCameraFragment =
			new MutableLiveEvent<>();
	private final MutableLiveData<CustodianTask.State> state =
			new MutableLiveData<>();
	final QrCodeDecoder qrCodeDecoder;

	@SuppressWarnings("CharsetObjectCanBeUsed") // Requires minSdkVersion >= 19
	private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");

	@Inject
	public CustodianReturnShardViewModel(
			@NonNull Application application,
			@IoExecutor Executor ioExecutor,
			AndroidExecutor androidExecutor) {
		super(application);

		this.androidExecutor = androidExecutor;
		this.ioExecutor = ioExecutor;
		qrCodeDecoder = new QrCodeDecoder(androidExecutor, ioExecutor, this);
	}

	@IoExecutor
	@Override
	public void onQrCodeDecoded(Result result) {
		LOG.info("Got result from decoder");
		// Ignore results until the KeyAgreementTask is ready
//		if (!gotLocalPayload || gotRemotePayload) return;
		try {
			byte[] payloadBytes = result.getText().getBytes(ISO_8859_1);
			if (LOG.isLoggable(INFO))
				LOG.info("Remote payload is " + payloadBytes.length + " bytes");
//			Payload remotePayload = payloadParser.parse(payloadBytes);
//			gotRemotePayload = true;
//			requireNonNull(task).connectAndRunProtocol(remotePayload);
//			state.postValue(new ReturnShardState.QrCodeScanned());
		} catch (IllegalArgumentException e) {
			LOG.log(WARNING, "QR Code Invalid", e);
			androidExecutor.runOnUiThread(() -> Toast.makeText(getApplication(),
					R.string.qr_code_invalid, LENGTH_LONG).show());
//			resetPayloadFlags();
			state.postValue(new CustodianTask.State.Failure(
					CustodianTask.State.Failure.Reason.QR_CODE_INVALID));
		}
	}

	@UiThread
	public void onContinueClicked() {
		wasContinueClicked = true;
//		checkPermissions.setEvent(true);
		showCameraFragment.setEvent(true);
	}

	QrCodeDecoder getQrCodeDecoder() {
		return qrCodeDecoder;
	}

	LiveEvent<Boolean> getShowCameraFragment() {
		return showCameraFragment;
	}

	LiveData<CustodianTask.State> getState() {
		return state;
	}
}
