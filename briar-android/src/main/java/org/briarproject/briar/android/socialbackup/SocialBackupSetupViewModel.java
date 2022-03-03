package org.briarproject.briar.android.socialbackup;

import android.app.Application;

import org.briarproject.bramble.api.connection.ConnectionRegistry;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.android.contact.ContactsViewModel;
import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.identity.AuthorManager;
import org.briarproject.briar.api.socialbackup.BackupMetadata;
import org.briarproject.briar.api.socialbackup.SocialBackupManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

public class SocialBackupSetupViewModel extends ContactsViewModel {

	private final SocialBackupManager socialBackupManager;
	private final DatabaseComponent db;
	private final ContactManager contactManager;
	private BackupMetadata backupMetadata;
	private List<ContactId> custodians;
	private int threshold;


	public enum State {
		EXPLAINING,
		CHOOSING_CUSTODIANS,
		GETTING_THRESHOLD,
		SUCCESS,
		FAILURE
	}

	private final MutableLiveData<State> state =
			new MutableLiveData<>();
	@Inject
	public SocialBackupSetupViewModel(
			@NonNull Application app,
			DatabaseComponent db,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			AuthorManager authorManager,
			ConversationManager conversationManager,
			ConnectionRegistry connectionRegistry,
			EventBus eventBus,
			AndroidExecutor androidExecutor,
			SocialBackupManager socialBackupManager,
			ContactManager contactManager
	) {
		super(app, dbExecutor, lifecycleManager, db, androidExecutor,
				contactManager, authorManager, conversationManager,
				connectionRegistry, eventBus);

		this.socialBackupManager = socialBackupManager;
		this.db = db;
		this.contactManager = contactManager;
	}

    public void loadCustodianList() {
	    try {
		    custodians = db.transactionWithResult(true,
				    socialBackupManager::getCustodianContactIds);
	    } catch (DbException e) {
	    	custodians = new ArrayList<>();
	    }
	    loadContacts();
    }

	public boolean haveExistingBackup() {
		try {
			backupMetadata = db.transactionWithNullableResult(true,
					socialBackupManager::getBackupMetadata);
		} catch (DbException e) {
			return false;
		}
		return (backupMetadata != null);
	}

//	public BackupMetadata getBackupMetadata() {
//		return backupMetadata;
//	}

	public boolean haveEnoughContacts() throws DbException {
		return (contactManager.getContacts().size() > 1);
	}

	public void setCustodians(List<ContactId> contacts) {
		custodians = contacts;
	}

	public void createBackup() {
		try {
			db.transaction(false, txn -> {
				socialBackupManager
						.createBackup(txn, (List<ContactId>) custodians,
								threshold);
			});
		} catch (DbException e) {
			state.postValue(State.FAILURE);
		}
	}

	public void setThreshold(int threshold) {
		this.threshold = threshold;
		createBackup();
	}

	public void onSuccessDismissed() {
		state.postValue(State.SUCCESS);
	}

	public MutableLiveData<State> getState() {
		return state;
	}

	public void onExplainerDismissed() {
		state.postValue(State.CHOOSING_CUSTODIANS);
	}

	@Override
	protected boolean displayContact(ContactId contactId) {
		// Check if contact holds a backup piece
		return custodians.contains(contactId);
	}

	public int getThresholdFromExistingBackup() {
		return backupMetadata.getThreshold();
	}

	public int getNumberOfCustodiansFromExistingBackup() {
		return backupMetadata.getCustodians().size();
	}
}

