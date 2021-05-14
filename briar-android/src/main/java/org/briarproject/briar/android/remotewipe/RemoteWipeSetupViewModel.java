package org.briarproject.briar.android.remotewipe;

import android.app.Application;
import android.provider.ContactsContract;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.briar.api.remotewipe.RemoteWipeManager;

import java.util.Collection;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

public class RemoteWipeSetupViewModel extends AndroidViewModel {
	private final RemoteWipeManager remoteWipeManager;
	private final DatabaseComponent db;

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

	public void setupRemoteWipe(Collection<ContactId> wipers)
			throws DbException, FormatException {
		db.transaction(false, txn -> {
			remoteWipeManager.setup(txn, (List<ContactId>) wipers);
		});
	}
}

