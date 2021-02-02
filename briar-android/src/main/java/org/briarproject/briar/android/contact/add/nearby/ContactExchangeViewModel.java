package org.briarproject.briar.android.contact.add.nearby;

import android.app.Application;
import android.graphics.Bitmap;
import android.util.DisplayMetrics;
import android.widget.Toast;

import com.google.zxing.Result;

import org.briarproject.bramble.api.UnsupportedVersionException;
import org.briarproject.bramble.api.connection.ConnectionManager;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactExchangeManager;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.ContactExistsException;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.keyagreement.KeyAgreementResult;
import org.briarproject.bramble.api.keyagreement.KeyAgreementTask;
import org.briarproject.bramble.api.keyagreement.Payload;
import org.briarproject.bramble.api.keyagreement.PayloadEncoder;
import org.briarproject.bramble.api.keyagreement.PayloadParser;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementAbortedEvent;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementFailedEvent;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementFinishedEvent;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementListeningEvent;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementStartedEvent;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementWaitingEvent;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.briar.R;
import org.briarproject.briar.android.contact.add.nearby.ContactAddingState.ContactExchangeFinished;
import org.briarproject.briar.android.contact.add.nearby.ContactAddingState.ContactExchangeStarted;
import org.briarproject.briar.android.contact.add.nearby.ContactAddingState.KeyAgreementListening;
import org.briarproject.briar.android.contact.add.nearby.ContactAddingState.KeyAgreementStarted;
import org.briarproject.briar.android.contact.add.nearby.ContactAddingState.KeyAgreementWaiting;
import org.briarproject.briar.android.contact.add.nearby.ContactExchangeResult.Error;
import org.briarproject.briar.android.contact.add.nearby.ContactExchangeResult.Success;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Provider;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static android.widget.Toast.LENGTH_LONG;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
class ContactExchangeViewModel extends AndroidViewModel
		implements EventListener, QrCodeDecoder.ResultCallback {

	private static final Logger LOG =
			getLogger(ContactExchangeViewModel.class.getName());

	@SuppressWarnings("CharsetObjectCanBeUsed") // Requires minSdkVersion >= 19
	private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");

	private final EventBus eventBus;
	private final Executor ioExecutor;
	private final PayloadEncoder payloadEncoder;
	private final PayloadParser payloadParser;
	private final Provider<KeyAgreementTask> keyAgreementTaskProvider;
	private final ContactExchangeManager contactExchangeManager;
	private final ConnectionManager connectionManager;

	private final MutableLiveData<ContactAddingState> state =
			new MutableLiveData<>();

	final QrCodeDecoder qrCodeDecoder;

	@Nullable
	private KeyAgreementTask task;
	private volatile boolean gotLocalPayload = false, gotRemotePayload = false;

	@Inject
	ContactExchangeViewModel(Application app,
			EventBus eventBus,
			@IoExecutor Executor ioExecutor,
			PayloadEncoder payloadEncoder,
			PayloadParser payloadParser,
			Provider<KeyAgreementTask> keyAgreementTaskProvider,
			ContactExchangeManager contactExchangeManager,
			ConnectionManager connectionManager) {
		super(app);
		this.eventBus = eventBus;
		this.ioExecutor = ioExecutor;
		this.payloadEncoder = payloadEncoder;
		this.payloadParser = payloadParser;
		this.keyAgreementTaskProvider = keyAgreementTaskProvider;
		this.contactExchangeManager = contactExchangeManager;
		this.connectionManager = connectionManager;
		qrCodeDecoder = new QrCodeDecoder(ioExecutor, this);
		eventBus.addListener(this);
	}

	@Override
	protected void onCleared() {
		super.onCleared();
		eventBus.removeListener(this);
		stopListening();
	}

	/**
	 * Call this once Bluetooth and Wi-Fi are ready to be used.
	 * It is possible to call this more than once over the ViewModel's lifetime.
	 */
	@UiThread
	void startListening() {
		KeyAgreementTask oldTask = task;
		KeyAgreementTask newTask = keyAgreementTaskProvider.get();
		task = newTask;
		ioExecutor.execute(() -> {
			if (oldTask != null) oldTask.stopListening();
			newTask.listen();
		});
	}

	@UiThread
	private void stopListening() {
		KeyAgreementTask oldTask = task;
		ioExecutor.execute(() -> {
			if (oldTask != null) oldTask.stopListening();
		});
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof KeyAgreementListeningEvent) {
			LOG.info("KeyAgreementListeningEvent received");
			KeyAgreementListeningEvent event = (KeyAgreementListeningEvent) e;
			onLocalPayloadReceived(event.getLocalPayload());
		} else if (e instanceof KeyAgreementWaitingEvent) {
			LOG.info("KeyAgreementWaitingEvent received");
			state.setValue(new KeyAgreementWaiting());
		} else if (e instanceof KeyAgreementStartedEvent) {
			LOG.info("KeyAgreementStartedEvent received");
			state.setValue(new KeyAgreementStarted());
		} else if (e instanceof KeyAgreementFinishedEvent) {
			LOG.info("KeyAgreementFinishedEvent received");
			KeyAgreementResult result =
					((KeyAgreementFinishedEvent) e).getResult();
			startContactExchange(result);
			state.setValue(new ContactExchangeStarted());
		} else if (e instanceof KeyAgreementAbortedEvent) {
			LOG.info("KeyAgreementAbortedEvent received");
			resetPayloadFlags();
			state.setValue(new ContactAddingState.Failed());
		} else if (e instanceof KeyAgreementFailedEvent) {
			LOG.info("KeyAgreementFailedEvent received");
			resetPayloadFlags();
			state.setValue(new ContactAddingState.Failed());
		}
	}

	/**
	 * This sets the QR code by setting the state to KeyAgreementListening.
	 */
	private void onLocalPayloadReceived(Payload localPayload) {
		if (gotLocalPayload) return;
		DisplayMetrics dm = getApplication().getResources().getDisplayMetrics();
		ioExecutor.execute(() -> {
			byte[] payloadBytes = payloadEncoder.encode(localPayload);
			if (LOG.isLoggable(INFO)) {
				LOG.info("Local payload is " + payloadBytes.length
						+ " bytes");
			}
			// Use ISO 8859-1 to encode bytes directly as a string
			String content = new String(payloadBytes, ISO_8859_1);
			Bitmap qrCode = QrCodeUtils.createQrCode(dm, content);
			gotLocalPayload = true;
			state.postValue(new KeyAgreementListening(qrCode));
		});
	}

	@Override
	@IoExecutor
	public void onQrCodeDecoded(Result result) {
		LOG.info("Got result from decoder");
		// Ignore results until the KeyAgreementTask is ready
		if (!gotLocalPayload || gotRemotePayload) return;
		try {
			byte[] payloadBytes = result.getText().getBytes(ISO_8859_1);
			if (LOG.isLoggable(INFO))
				LOG.info("Remote payload is " + payloadBytes.length + " bytes");
			Payload remotePayload = payloadParser.parse(payloadBytes);
			gotRemotePayload = true;
			requireNonNull(task).connectAndRunProtocol(remotePayload);
			state.postValue(new ContactAddingState.QrCodeScanned());
		} catch (UnsupportedVersionException e) {
			resetPayloadFlags();
			state.postValue(new ContactAddingState.Failed(e.isTooOld()));
		} catch (IOException | IllegalArgumentException e) {
			LOG.log(WARNING, "QR Code Invalid", e);
			Toast.makeText(getApplication(), R.string.qr_code_invalid,
					LENGTH_LONG).show();
			resetPayloadFlags();
			state.postValue(new ContactAddingState.Failed());
		}
	}

	private void resetPayloadFlags() {
		gotRemotePayload = false;
		gotLocalPayload = false;
	}

	@UiThread
	private void startContactExchange(KeyAgreementResult result) {
		TransportId t = result.getTransportId();
		DuplexTransportConnection conn = result.getConnection();
		SecretKey masterKey = result.getMasterKey();
		boolean alice = result.wasAlice();
		ioExecutor.execute(() -> {
			try {
				Contact contact = contactExchangeManager.exchangeContacts(conn,
						masterKey, alice, true);
				// Reuse the connection as a transport connection
				connectionManager
						.manageOutgoingConnection(contact.getId(), t, conn);
				Success success = new Success(contact.getAuthor());
				state.postValue(new ContactExchangeFinished(success));
			} catch (ContactExistsException e) {
				tryToClose(conn);
				Error error = new Error(e.getRemoteAuthor());
				state.postValue(new ContactExchangeFinished(error));
			} catch (DbException | IOException e) {
				tryToClose(conn);
				logException(LOG, WARNING, e);
				Error error = new Error(null);
				state.postValue(new ContactExchangeFinished(error));
			}
		});
	}

	private void tryToClose(DuplexTransportConnection conn) {
		try {
			conn.getReader().dispose(true, true);
			conn.getWriter().dispose(true);
		} catch (IOException e) {
			logException(LOG, WARNING, e);
		}
	}

	LiveData<ContactAddingState> getState() {
		return state;
	}

}
