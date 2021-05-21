package org.briarproject.briar.android.remotewipe;

import android.app.Application;
import android.provider.ContactsContract;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.briar.api.remotewipe.RemoteWipeManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

public class RemoteWipeSetupViewModel extends AndroidViewModel {
	private final RemoteWipeManager remoteWipeManager;
	private final DatabaseComponent db;
	private final MutableLiveData<RemoteWipeSetupState> state = new MutableLiveData<>();

	@Inject
	RemoteWipeSetupViewModel(
			@NonNull Application application,
			RemoteWipeManager remoteWipeManager,
			DatabaseComponent db) {
		super(application);
		this.remoteWipeManager = remoteWipeManager;
		this.db = db;
	}

	public boolean remoteWipeIsSetup() {
		try {
			return db.transactionWithResult(true,
					txn -> remoteWipeManager.remoteWipeIsSetup(txn));
		} catch (DbException e) {
			return false;
		}
	}

	public List<String> getWiperNames() {
		ArrayList wiperNames = new ArrayList();
		try {
			List<Author> wipers = db.transactionWithResult(true,
					txn -> remoteWipeManager.getWipers(txn));
			for (Author wiper : wipers) {
				wiperNames.add(wiper.getName());
			}
			return wiperNames;
		} catch (DbException ignored) {
			// Will return an empty list
		}
		return wiperNames;
	}

	@UiThread
	public void onSuccessDismissed() {
		state.postValue(RemoteWipeSetupState.FINISHED);
	}

	public void setupRemoteWipe(Collection<ContactId> wipers)
			throws DbException, FormatException {
		db.transaction(false, txn -> {
			remoteWipeManager.setup(txn, (List<ContactId>) wipers);
			state.postValue(RemoteWipeSetupState.SUCCESS);
		});
	}

	public MutableLiveData<RemoteWipeSetupState> getState() {
		return state;
	}
}

