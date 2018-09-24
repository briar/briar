package org.briarproject.briar.android.contact.add.remote;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Logger.getLogger;

@NotNullByDefault
public class AddContactViewModel extends AndroidViewModel {

	private static Logger LOG = getLogger(AddContactViewModel.class.getName());

	private final ContactManager contactManager;
	@DatabaseExecutor
	private final Executor dbExecutor;

	private final MutableLiveData<String> ourLink = new MutableLiveData<>();
	private final MutableLiveData<Boolean> remoteLinkEntered =
			new MutableLiveData<>();
	@Nullable
	private String remoteContactLink;

	@Inject
	public AddContactViewModel(@NonNull Application application,
			ContactManager contactManager,
			@DatabaseExecutor Executor dbExecutor) {
		super(application);
		this.contactManager = contactManager;
		this.dbExecutor = dbExecutor;
		loadOurLink();
	}

	private void loadOurLink() {
		dbExecutor.execute(() -> {
			try {
				ourLink.postValue(contactManager.getRemoteContactLink());
			} catch (DbException e) {
				throw new AssertionError(e);
			}
		});
	}

	LiveData<String> getOurLink() {
		return ourLink;
	}

	void setRemoteContactLink(String link) {
		remoteContactLink = link;
	}

	@Nullable
	String getRemoteContactLink() {
		return remoteContactLink;
	}

	boolean isValidRemoteContactLink(@Nullable CharSequence link) {
		return link != null &&
				contactManager.isValidRemoteContactLink(link.toString());
	}

	LiveData<Boolean> getRemoteLinkEntered() {
		return remoteLinkEntered;
	}

	void onRemoteLinkEntered() {
		if (remoteContactLink == null) throw new IllegalStateException();
		remoteLinkEntered.setValue(true);
	}

	void addContact(String nickname) {
		if (remoteContactLink == null) throw new IllegalStateException();
		contactManager.addRemoteContactRequest(remoteContactLink, nickname);
	}

}
