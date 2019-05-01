package org.briarproject.briar.android.contact.add.remote;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.contact.ContactManager.LINK_REGEX;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
public class AddContactViewModel extends AndroidViewModel {

	private final static Logger LOG =
			getLogger(AddContactViewModel.class.getName());

	private final ContactManager contactManager;
	@DatabaseExecutor
	private final Executor dbExecutor;

	private final MutableLiveData<String> handshakeLink =
			new MutableLiveData<>();
	private final MutableLiveData<Boolean> remoteLinkEntered =
			new MutableLiveData<>();
	private final MutableLiveData<Boolean> addContactResult =
			new MutableLiveData<>();
	@Nullable
	private String remoteHandshakeLink;

	@Inject
	public AddContactViewModel(@NonNull Application application,
			ContactManager contactManager,
			@DatabaseExecutor Executor dbExecutor) {
		super(application);
		this.contactManager = contactManager;
		this.dbExecutor = dbExecutor;
		loadHandshakeLink();
	}

	private void loadHandshakeLink() {
		dbExecutor.execute(() -> {
			try {
				handshakeLink.postValue(contactManager.getHandshakeLink());
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				// the UI should stay disable in this case,
				// leaving the user unable to proceed
			}
		});
	}

	LiveData<String> getHandshakeLink() {
		return handshakeLink;
	}

	void setRemoteHandshakeLink(String link) {
		remoteHandshakeLink = link;
	}

	boolean isValidRemoteContactLink(@Nullable CharSequence link) {
		return link != null && LINK_REGEX.matcher(link).find();
	}

	LiveData<Boolean> getRemoteLinkEntered() {
		return remoteLinkEntered;
	}

	void onRemoteLinkEntered() {
		if (remoteHandshakeLink == null) throw new IllegalStateException();
		remoteLinkEntered.setValue(true);
		remoteLinkEntered.postValue(false);  // reset, so we can navigate back
	}

	void addContact(String nickname) {
		if (remoteHandshakeLink == null) throw new IllegalStateException();
		dbExecutor.execute(() -> {
			try {
				contactManager.addPendingContact(remoteHandshakeLink, nickname);
				addContactResult.postValue(true);
			} catch (DbException | FormatException e) {
				logException(LOG, WARNING, e);
				addContactResult.postValue(false);
			}
		});
	}

	public LiveData<Boolean> getAddContactResult() {
		return addContactResult;
	}

}
