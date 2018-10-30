package org.briarproject.briar.android.contact;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Transformations;
import android.support.annotation.NonNull;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.NoSuchContactException;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.briar.android.AndroidComponent;
import org.briarproject.briar.android.BriarApplication;
import org.briarproject.briar.android.util.UiUtils;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;

public class ConversationViewModel extends AndroidViewModel {

	private static Logger LOG =
			Logger.getLogger(ConversationViewModel.class.getName());

	@Inject
	@DatabaseExecutor
	Executor dbExecutor;
	@Inject
	ContactManager contactManager;

	private final MutableLiveData<Contact> contact = new MutableLiveData<>();
	private final LiveData<AuthorId> contactAuthorId =
			Transformations.map(contact, c -> c.getAuthor().getId());
	private final LiveData<String> contactName =
			Transformations.map(contact, UiUtils::getContactDisplayName);
	private final MutableLiveData<Boolean> contactDeleted =
			new MutableLiveData<>();

	public ConversationViewModel(@NonNull Application application) {
		super(application);
		AndroidComponent component =
				((BriarApplication) application).getApplicationComponent();
		component.inject(this);
		contactDeleted.setValue(false);
	}

	void loadContact(ContactId contactId) {
		dbExecutor.execute(() -> {
			try {
				long start = now();
				contact.postValue(contactManager.getContact(contactId));
				logDuration(LOG, "Loading contact", start);
			} catch (NoSuchContactException e) {
				contactDeleted.postValue(true);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	void setContactAlias(ContactId contactId, String alias) {
		dbExecutor.execute(() -> {
			try {
				contactManager.setContactAlias(contactId,
						alias.isEmpty() ? null : alias);
				loadContact(contactId);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	LiveData<Contact> getContact() {
		return contact;
	}

	LiveData<AuthorId> getContactAuthorId() {
		return contactAuthorId;
	}

	LiveData<String> getContactDisplayName() {
		return contactName;
	}

	LiveData<Boolean> isContactDeleted() {
		return contactDeleted;
	}

}
