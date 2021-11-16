package org.briarproject.briar.android.mailbox;

import android.app.Application;

import com.google.zxing.Result;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.android.qrcode.QrCodeDecoder;
import org.briarproject.briar.android.viewmodel.DbViewModel;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;

@UiThread
@NotNullByDefault
class MailboxPairViewModel extends DbViewModel
		implements QrCodeDecoder.ResultCallback {
	private static final Logger LOG =
			getLogger(MailboxPairViewModel.class.getName());

	private static final int VERSION_REQUIRED = 32;

	@SuppressWarnings("CharsetObjectCanBeUsed") // Requires minSdkVersion >= 19
	private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");

	private final CryptoComponent crypto;
	private final QrCodeDecoder qrCodeDecoder;

	@Nullable
	private String onionAddress = null;
	@Nullable
	private String setupToken = null;

	@Inject
	MailboxPairViewModel(
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
	}

	@Override
	public void onQrCodeDecoded(Result result) {
		LOG.info("Got result from decoder");
		byte[] bytes = result.getText().getBytes(ISO_8859_1);

		if (LOG.isLoggable(INFO))
			LOG.info("QR code length in bytes: " + bytes.length);
		if (bytes.length != 65) {
			LOG.info("QR code has wrong length");
			return;
		}

		if (LOG.isLoggable(INFO))
			LOG.info("QR code version: " + bytes[0]);
		if (bytes[0] != VERSION_REQUIRED) {
			LOG.info("QR code has wrong version");
			return;
		}

		byte[] onionPubKey = Arrays.copyOfRange(bytes, 1, 33);
		onionAddress = crypto.encodeOnionAddress(onionPubKey);
		setupToken = StringUtils.toHexString(Arrays.copyOfRange(bytes, 33, 65))
				.toLowerCase();
		LOG.info("QR code is valid");
	}

	QrCodeDecoder getQrCodeDecoder() {
		return qrCodeDecoder;
	}

}
