package org.briarproject.briar.android.keyagreement;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.support.annotation.UiThread;

import org.briarproject.bramble.api.contact.ContactExchangeManager;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.ContactExistsException;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
class ContactExchangeViewModel extends AndroidViewModel {

	private static final Logger LOG =
			getLogger(ContactExchangeViewModel.class.getName());

	private final Executor ioExecutor;
	private final ContactExchangeManager contactExchangeManager;
	private final MutableLiveData<Boolean> succeeded = new MutableLiveData<>();

	@Nullable
	private volatile Author remoteAuthor, duplicateAuthor;

	@Inject
	ContactExchangeViewModel(Application app, @IoExecutor Executor ioExecutor,
			ContactExchangeManager contactExchangeManager) {
		super(app);
		this.ioExecutor = ioExecutor;
		this.contactExchangeManager = contactExchangeManager;
	}

	@UiThread
	void startContactExchange(TransportId t, DuplexTransportConnection conn,
			SecretKey masterKey, boolean alice) {
		ioExecutor.execute(() -> {
			try {
				remoteAuthor = contactExchangeManager.exchangeContacts(t, conn,
						masterKey, alice);
				succeeded.postValue(true);
			} catch (ContactExistsException e) {
				duplicateAuthor = e.getRemoteAuthor();
				succeeded.postValue(false);
			} catch (DbException | IOException e) {
				logException(LOG, WARNING, e);
				succeeded.postValue(false);
			}
		});
	}

	@UiThread
	@Nullable
	Author getRemoteAuthor() {
		return remoteAuthor;
	}

	@UiThread
	@Nullable
	Author getDuplicateAuthor() {
		return duplicateAuthor;
	}

	LiveData<Boolean> getSucceeded() {
		return succeeded;
	}
}
