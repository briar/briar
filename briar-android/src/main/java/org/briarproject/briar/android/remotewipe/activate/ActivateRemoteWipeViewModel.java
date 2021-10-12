package org.briarproject.briar.android.remotewipe.activate;

import android.app.Application;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.briar.android.remotewipe.RemoteWipeSetupState;
import org.briarproject.briar.android.util.UiUtils;
import org.briarproject.briar.api.remotewipe.RemoteWipeManager;

import java.text.Normalizer;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

public class ActivateRemoteWipeViewModel extends AndroidViewModel {

	private final RemoteWipeManager remoteWipeManager;
	private final ContactManager contactManager;
	private final DatabaseComponent db;
	private final MutableLiveData<ActivateRemoteWipeState> state = new MutableLiveData<>();
	private ContactId contactId;
	private String contactName;

	@Inject
	public ActivateRemoteWipeViewModel(
			@NonNull Application application,
			RemoteWipeManager remoteWipeManager,
			ContactManager contactManager,
			DatabaseComponent db) {
		super(application);
		this.remoteWipeManager = remoteWipeManager;
		this.contactManager = contactManager;
		this.db = db;
	}

	private void activateWipe() {
		try {
			db.transaction(false, txn -> {
				try {
					remoteWipeManager.wipe(txn, db.getContact(txn, contactId));
				} catch (DbException e) {
					state.postValue(ActivateRemoteWipeState.FAILED);
				} catch (FormatException e) {
					state.postValue(ActivateRemoteWipeState.FAILED);
				}
				state.postValue(ActivateRemoteWipeState.SUCCESS);
			});
		} catch (DbException e) {
			state.postValue(ActivateRemoteWipeState.FAILED);
		}
	}

	public MutableLiveData<ActivateRemoteWipeState> getState() {
		return state;
	}

	public void setContactId(ContactId c) {
		contactId = c;

		try {
			contactName = UiUtils.getContactDisplayName(contactManager.getContact(c));
		} catch (DbException e) {
			e.printStackTrace();
		}
	}

	public String getContactName() {
		return contactName;
	}

	public void onCancelClicked() {
        state.postValue(ActivateRemoteWipeState.CANCELLED);
	}

	public void onSuccessDismissed() {
		state.postValue(ActivateRemoteWipeState.FINISHED);
	}

	public void onConfirmClicked() {
		activateWipe();
	}
}
