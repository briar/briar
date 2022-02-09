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
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.android.mailbox.MailboxState.NotSetup;
import org.briarproject.briar.android.qrcode.QrCodeDecoder;
import org.briarproject.briar.android.viewmodel.DbViewModel;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.UiThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;

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

	private final MutableLiveData<MailboxState> state = new MutableLiveData<>();

	@Inject
	MailboxViewModel(
			Application app,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			TransactionManager db,
			AndroidExecutor androidExecutor,
			@IoExecutor Executor ioExecutor,
			CryptoComponent crypto) {
		super(app, dbExecutor, lifecycleManager, db, androidExecutor);
		this.crypto = crypto;
		qrCodeDecoder = new QrCodeDecoder(androidExecutor, ioExecutor, this);
		checkIfSetup();
	}

	@UiThread
	private void checkIfSetup() {
		runOnDbThread(() -> {
			// TODO really check if mailbox is setup/paired/linked
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			state.postValue(new NotSetup());
		});
	}

	@Override
	@IoExecutor
	public void onQrCodeDecoded(Result result) {
		LOG.info("Got result from decoder");
		try {
			// TODO pass props to core (maybe even do payload parsing there)
			MailboxProperties properties = decodeQrCode(result.getText());
		} catch (FormatException e) {
			state.postValue(new MailboxState.QrCodeWrong());
			return;
		}
		state.postValue(new MailboxState.SettingUp());
	}

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

	@UiThread
	QrCodeDecoder getQrCodeDecoder() {
		return qrCodeDecoder;
	}

	@UiThread
	LiveData<MailboxState> getState() {
		return state;
	}

}
