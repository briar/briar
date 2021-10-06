package org.briarproject.briar.android.socialbackup;

import android.os.Bundle;
import android.widget.Toast;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.contactselection.ContactSelectorListener;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.api.socialbackup.BackupMetadata;
import org.briarproject.briar.api.socialbackup.SocialBackupManager;

import java.util.Collection;
import java.util.List;

import javax.inject.Inject;

public class DistributedBackupActivity extends BriarActivity implements
		BaseFragment.BaseFragmentListener, ContactSelectorListener,
		ThresholdDefinedListener,
		ShardsSentFragment.ShardsSentDismissedListener {

	private Collection<ContactId> custodians;

	@Inject
	public SocialBackupManager socialBackupManager;

	@Inject
	public ContactManager contactManager;

	@Inject
	public DatabaseComponent db;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_distributed_backup);

		try {
			db.transaction(false, txn -> {
				BackupMetadata backupMetadata =
						socialBackupManager.getBackupMetadata(txn);
				if (backupMetadata == null) throw new DbException();
				ExistingBackupFragment fragment =
						ExistingBackupFragment.newInstance(backupMetadata);
				showInitialFragment(fragment);
			});
		} catch (DbException e) {
			// Check the number of contacts in the contacts list > 1
			try {
				if (contactManager.getContacts().size() < 2) {
					Toast.makeText(this,
							R.string.social_backup_not_enough_contacts,
							Toast.LENGTH_LONG).show();
					finish();
				}
			} catch (DbException dbException) {
				Toast.makeText(this,
						R.string.reading_contacts_error,
						Toast.LENGTH_LONG).show();
				finish();
			}
			CustodianSelectorFragment fragment =
					CustodianSelectorFragment.newInstance();
			showInitialFragment(fragment);
		}
	}

	@Override
	public void contactsSelected(Collection<ContactId> contacts) {
		Toast.makeText(this,
				String.format("Selected %d contacts", contacts.size()),
				Toast.LENGTH_SHORT).show();
		custodians = contacts;
		ThresholdSelectorFragment fragment =
				ThresholdSelectorFragment.newInstance(contacts.size());
		showNextFragment(fragment);
	}

	@Override
	public void thresholdDefined(int threshold) {
		try {
			db.transaction(false, txn -> {
				socialBackupManager
						.createBackup(txn, (List<ContactId>) custodians,
								threshold);
				ShardsSentFragment fragment = new ShardsSentFragment();
				showNextFragment(fragment);
			});
		} catch (DbException e) {
			Toast.makeText(this,
					"There was an error when creating the backup",
					Toast.LENGTH_LONG).show();
			finish();
		}
	}

	@Override
	public void shardsSentDismissed() {
		finish();
	}
}
