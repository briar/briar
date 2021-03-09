package org.briarproject.briar.android.socialbackup;

import android.os.Bundle;
import android.widget.Toast;

import androidx.fragment.app.FragmentTransaction;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.contactselection.ContactSelectorListener;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.api.socialbackup.BackupMetadata;
import org.briarproject.briar.api.socialbackup.Shard;
import org.briarproject.briar.api.socialbackup.SocialBackupManager;

import java.util.Collection;
import java.util.List;

import javax.inject.Inject;

public class DistributedBackupActivity extends BriarActivity implements
		BaseFragment.BaseFragmentListener, ContactSelectorListener,
		ThresholdDefinedListener, ShardsSentDismissedListener {

	private Collection<ContactId> custodians;

	@Inject
    public SocialBackupManager socialBackupManager;

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
        // TODO here we should check if we already have a backup

		try {
			db.transaction(false, txn -> {
				BackupMetadata backupMetadata = socialBackupManager.getBackupMetadata(txn);
				 if (backupMetadata == null) {
				CustodianSelectorFragment fragment =
						CustodianSelectorFragment.newInstance();
					 showInitialFragment(fragment);
				 } else {
				 	 // TODO make a fragment to display the backup metadata
					 ShardsSentFragment fragment = new ShardsSentFragment();
					 showInitialFragment(fragment);
				 }
			});
		} catch (DbException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void contactsSelected(Collection<ContactId> contacts) {
		Toast.makeText(this,
				String.format("selected %d contacts", contacts.size()),
				Toast.LENGTH_SHORT).show();
		custodians = contacts;
		ThresholdSelectorFragment fragment = ThresholdSelectorFragment.newInstance(contacts.size());
		showNextFragment(fragment);
	}

	@Override
	public void thresholdDefined(int threshold) throws DbException {
		db.transaction(false, txn -> {
			socialBackupManager.createBackup(txn, (List<ContactId>) custodians, threshold);
			ShardsSentFragment fragment = new ShardsSentFragment();
			showNextFragment(fragment);
		});
	}

	@Override
	public void shardsSentDismissed() {
		finish();
	}
}
