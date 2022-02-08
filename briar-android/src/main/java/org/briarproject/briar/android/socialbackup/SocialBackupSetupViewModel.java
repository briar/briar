package org.briarproject.briar.android.socialbackup;

import android.app.Application;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.briar.api.socialbackup.BackupMetadata;
import org.briarproject.briar.api.socialbackup.SocialBackupManager;

import java.util.Collection;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

public class SocialBackupSetupViewModel extends AndroidViewModel {

	private final SocialBackupManager socialBackupManager;
	private final DatabaseComponent db;
	private final ContactManager contactManager;
	private BackupMetadata backupMetadata;
	private Collection<ContactId> custodians;
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
			SocialBackupManager socialBackupManager,
			ContactManager contactManager
	) {
		super(app);
		this.socialBackupManager = socialBackupManager;
		this.db = db;
		this.contactManager = contactManager;
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

	public BackupMetadata getBackupMetadata() {
		return backupMetadata;
	}

	public boolean haveEnoughContacts() throws DbException {
		return (contactManager.getContacts().size() > 1);
	}

	public void setCustodians(Collection<ContactId> contacts) {
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
}

