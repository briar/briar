package org.briarproject.briar.android.contact.add.nearby;

import android.app.Application;

import org.briarproject.bramble.api.connection.ConnectionManager;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactExchangeManager;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.ContactExistsException;
import org.briarproject.bramble.api.db.DbException;
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

@NotNullByDefault
class ContactExchangeViewModel extends AndroidViewModel {

	private static final Logger LOG =
			getLogger(ContactExchangeViewModel.class.getName());

	private final Executor ioExecutor;
	private final ContactExchangeManager contactExchangeManager;
	private final ConnectionManager connectionManager;
	private final MutableLiveData<ContactExchangeResult> exchangeResult =
			new MutableLiveData<>();

	@Inject
	ContactExchangeViewModel(Application app, @IoExecutor Executor ioExecutor,
			ContactExchangeManager contactExchangeManager,
			ConnectionManager connectionManager) {
		super(app);
		this.ioExecutor = ioExecutor;
		this.contactExchangeManager = contactExchangeManager;
		this.connectionManager = connectionManager;
	}

	@UiThread
	void startContactExchange(TransportId t, DuplexTransportConnection conn,
			SecretKey masterKey, boolean alice) {
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

	LiveData<ContactExchangeResult> getContactExchangeResult() {
		return exchangeResult;
	}
}
