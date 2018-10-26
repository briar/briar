package org.briarproject.briar.android.contact;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
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

	private final MutableLiveData<AuthorId> contactAuthorId =
			new MutableLiveData<>();
	private final MutableLiveData<Boolean> contactDeleted =
			new MutableLiveData<>();
	private final MutableLiveData<String> contactName = new MutableLiveData<>();

	public ConversationViewModel(@NonNull Application application) {
		super(application);
		AndroidComponent component =
				((BriarApplication) application).getApplicationComponent();
		component.inject(this);
		contactDeleted.setValue(false);
	}

	void loadContactDetails(ContactId contactId) {
		dbExecutor.execute(() -> {
			try {
				long start = now();
				Contact contact = contactManager.getContact(contactId);
				contactAuthorId.postValue(contact.getAuthor().getId());
				contactName.postValue(UiUtils.getContactDisplayName(contact));
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
				loadContactDetails(contactId);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	LiveData<AuthorId> getContactAuthorId() {
		return contactAuthorId;
	}

	LiveData<Boolean> isContactDeleted() {
		return contactDeleted;
	}

	LiveData<String> getContactDisplayName() {
		return contactName;
	}

}
