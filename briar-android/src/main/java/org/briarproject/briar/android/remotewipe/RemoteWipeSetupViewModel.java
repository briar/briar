package org.briarproject.briar.android.remotewipe;

import android.app.Application;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.connection.ConnectionRegistry;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.android.contact.ContactsViewModel;
import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.identity.AuthorManager;
import org.briarproject.briar.api.remotewipe.RemoteWipeManager;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.lifecycle.MutableLiveData;

@NotNullByDefault
public class RemoteWipeSetupViewModel extends ContactsViewModel {
	private final RemoteWipeManager remoteWipeManager;
	private final DatabaseComponent db;
	private List<ContactId> wiperContactIds;
	private final MutableLiveData<RemoteWipeSetupState> state =
			new MutableLiveData<>();

	private final ContactManager contactManager;
	private final AuthorManager authorManager;

	@Inject
	RemoteWipeSetupViewModel(
			@NonNull Application application,
			RemoteWipeManager remoteWipeManager,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			AuthorManager authorManager,
			ConversationManager conversationManager,
			ConnectionRegistry connectionRegistry,
			EventBus eventBus,
			AndroidExecutor androidExecutor,
			ContactManager contactManager,
			DatabaseComponent db) {
		super(application, dbExecutor, lifecycleManager, db, androidExecutor,
				contactManager, authorManager, conversationManager,
				connectionRegistry, eventBus);
		this.remoteWipeManager = remoteWipeManager;
		this.contactManager = contactManager;
		this.authorManager = authorManager;
		this.db = db;
		getWiperContactIds();
		loadContacts();
	}

	public boolean remoteWipeIsSetup() {
		try {
			return db.transactionWithResult(true,
					txn -> {
						boolean isSetup = remoteWipeManager.remoteWipeIsSetup(txn);
						if (isSetup) wiperContactIds = remoteWipeManager.getWiperContactIds(txn);
						return isSetup;
					});
		} catch (DbException e) {
			return false;
		}
	}

	public List<ContactId> getWiperContactIds() {
		try {
			wiperContactIds = db.transactionWithResult(true,
					remoteWipeManager::getWiperContactIds);
		} catch (DbException ignored) {
			return new ArrayList<ContactId>();
		}
		return wiperContactIds;
	}

	@UiThread
	public void onExplainerConfirmed() {
		state.postValue(RemoteWipeSetupState.SELECTING);
	}

	@UiThread
	public void onExplainerCancelled() {
		state.postValue(RemoteWipeSetupState.FINISHED);
	}

	@UiThread
	public void onSuccessDismissed() {
		state.postValue(RemoteWipeSetupState.FINISHED);
	}

	@UiThread
	public void onModifyWipers() {
		state.postValue(RemoteWipeSetupState.MODIFY);
	}

	@UiThread
	public void onDisableRemoteWipe() {
		try {
			db.transaction(false, remoteWipeManager::revokeAll);
			state.postValue(RemoteWipeSetupState.DISABLED);
		} catch (DbException | FormatException e) {
			e.printStackTrace();
			state.postValue(RemoteWipeSetupState.FINISHED);
		}
	}

	public void setupRemoteWipe(Collection<ContactId> wipers)
			throws DbException, FormatException {
		db.transaction(false, txn -> {
			remoteWipeManager.setup(txn, (List<ContactId>) wipers);
			state.postValue(RemoteWipeSetupState.SUCCESS);
		});
	}

	@Override
	protected boolean displayContact(ContactId contactId) {
		// Check if contact is a wiper
		return wiperContactIds.contains(contactId);
	}

	public MutableLiveData<RemoteWipeSetupState> getState() {
		return state;
	}
}

