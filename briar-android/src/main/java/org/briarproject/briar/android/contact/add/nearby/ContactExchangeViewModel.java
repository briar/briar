package org.briarproject.briar.android.contact.add.nearby;

import android.app.Application;

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
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementAbortedEvent;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementFailedEvent;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementFinishedEvent;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementStartedEvent;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementWaitingEvent;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.briar.android.contact.add.nearby.ContactExchangeResult.Error;
import org.briarproject.briar.android.contact.add.nearby.ContactExchangeResult.Success;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.UiThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.android.contact.add.nearby.ContactExchangeViewModel.KeyAgreementState.ABORTED;
import static org.briarproject.briar.android.contact.add.nearby.ContactExchangeViewModel.KeyAgreementState.FAILED;
import static org.briarproject.briar.android.contact.add.nearby.ContactExchangeViewModel.KeyAgreementState.FINISHED;
import static org.briarproject.briar.android.contact.add.nearby.ContactExchangeViewModel.KeyAgreementState.STARTED;
import static org.briarproject.briar.android.contact.add.nearby.ContactExchangeViewModel.KeyAgreementState.WAITING;

@NotNullByDefault
class ContactExchangeViewModel extends AndroidViewModel
		implements EventListener {

	private static final Logger LOG =
			getLogger(ContactExchangeViewModel.class.getName());

	enum KeyAgreementState {
		WAITING, STARTED, FINISHED, ABORTED, FAILED
	}

	private final EventBus eventBus;
	private final Executor ioExecutor;
	private final ContactExchangeManager contactExchangeManager;
	private final ConnectionManager connectionManager;
	private final MutableLiveData<KeyAgreementState> keyAgreementState =
			new MutableLiveData<>();
	private final MutableLiveData<ContactExchangeResult> exchangeResult =
			new MutableLiveData<>();

	@Inject
	ContactExchangeViewModel(Application app,
			EventBus eventBus,
			@IoExecutor Executor ioExecutor,
			ContactExchangeManager contactExchangeManager,
			ConnectionManager connectionManager) {
		super(app);
		this.eventBus = eventBus;
		this.ioExecutor = ioExecutor;
		this.contactExchangeManager = contactExchangeManager;
		this.connectionManager = connectionManager;
	}

	@Override
	protected void onCleared() {
		super.onCleared();
		eventBus.removeListener(this);
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof KeyAgreementWaitingEvent) {
			keyAgreementState.setValue(WAITING);
		} else if (e instanceof KeyAgreementStartedEvent) {
			keyAgreementState.setValue(STARTED);
		} else if (e instanceof KeyAgreementAbortedEvent) {
			keyAgreementState.setValue(ABORTED);
		} else if (e instanceof KeyAgreementFinishedEvent) {
			keyAgreementState.setValue(FINISHED);
			KeyAgreementResult result =
					((KeyAgreementFinishedEvent) e).getResult();
			startContactExchange(result);
		} else if (e instanceof KeyAgreementFailedEvent) {
			keyAgreementState.setValue(FAILED);
		}
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
				exchangeResult.postValue(new Success(contact.getAuthor()));
			} catch (ContactExistsException e) {
				tryToClose(conn);
				exchangeResult.postValue(new Error(e.getRemoteAuthor()));
			} catch (DbException | IOException e) {
				tryToClose(conn);
				logException(LOG, WARNING, e);
				exchangeResult.postValue(new Error(null));
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

	LiveData<KeyAgreementState> getKeyAgreementState() {
		return keyAgreementState;
	}

	LiveData<ContactExchangeResult> getContactExchangeResult() {
		return exchangeResult;
	}
}
