package org.briarproject.briar.android.remotewipe.revoke;

import android.app.Application;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.briar.android.remotewipe.activate.ActivateRemoteWipeState;
import org.briarproject.briar.api.remotewipe.RemoteWipeManager;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

public class RevokeRemoteWipeViewModel extends AndroidViewModel {

	private final RemoteWipeManager remoteWipeManager;
	private final DatabaseComponent db;
	private final MutableLiveData<RevokeRemoteWipeState> state = new MutableLiveData<>();
	private ContactId contactId;

	@Inject
	public RevokeRemoteWipeViewModel(
			@NonNull Application application,
			RemoteWipeManager remoteWipeManager,
			DatabaseComponent db) {
		super(application);
		this.remoteWipeManager = remoteWipeManager;
		this.db = db;
	}

	public MutableLiveData<RevokeRemoteWipeState> getState() {
		return state;
	}

	public void revokeRemoteWipeStatus(ContactId c) {
		contactId = c;
		try {
			db.transaction(false, txn -> {
				remoteWipeManager.revoke(txn, contactId);
			});
		} catch (DbException e) {
			state.postValue(RevokeRemoteWipeState.FAILED);
		} catch (FormatException e) {
			state.postValue(RevokeRemoteWipeState.FAILED);
		}
		state.postValue(RevokeRemoteWipeState.SUCCESS);
	}

	public void onCancelClicked() {
//		state.postValue(ActivateRemoteWipeState.CANCELLED);
	}

	public void onSuccessDismissed() {
		state.postValue(RevokeRemoteWipeState.FINISHED);
	}
}
