package org.briarproject.briar.android.socialbackup;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.api.socialbackup.BackupMetadata;
import org.briarproject.briar.api.socialbackup.SocialBackupManager;

import javax.inject.Inject;

import static org.briarproject.briar.android.conversation.ConversationActivity.CONTACT_ID;

public class CustodianHelpRecoverActivity extends BriarActivity implements
		BaseFragment.BaseFragmentListener, CustodianScanQrButtonListener {
	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Inject
	public SocialBackupManager socialBackupManager;

	@Inject
	public DatabaseComponent db;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_recover); // TODO change this

		Intent intent = getIntent();
		int id = intent.getIntExtra(CONTACT_ID, -1);
		if (id == -1) throw new IllegalStateException("No ContactId");
		ContactId contactId = new ContactId(id);

        // check if we have a shard for this secret owner
		try {
			db.transaction(false, txn -> {
				if (!socialBackupManager.amCustodian(txn, contactId)) {
					throw new DbException();
				}
				CustodianRecoveryModeExplainerFragment fragment =
						new CustodianRecoveryModeExplainerFragment();
				showInitialFragment(fragment);
			});
		} catch (DbException e) {
			// TODO improve this
			Toast.makeText(this,
					"You do not hold a backup shard from this contact",
					Toast.LENGTH_SHORT).show();
			finish();
		}
	}

	@Override
	public void scanQrButtonClicked() {
		// TODO scan qr code
		finish();
	}
}
