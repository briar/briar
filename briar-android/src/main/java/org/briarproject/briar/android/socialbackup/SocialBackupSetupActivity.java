package org.briarproject.briar.android.socialbackup;

import android.os.Bundle;
import android.widget.Toast;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.contactselection.ContactSelectorListener;
import org.briarproject.briar.android.fragment.BaseFragment;

import java.util.Collection;

import javax.inject.Inject;

import androidx.lifecycle.ViewModelProvider;

public class SocialBackupSetupActivity extends BriarActivity implements
		BaseFragment.BaseFragmentListener, ContactSelectorListener {

	private SocialBackupSetupViewModel viewModel;

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(SocialBackupSetupViewModel.class);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_distributed_backup);

		if (viewModel.haveExistingBackup()) {
			showInitialFragment(new ExistingBackupFragment());
		} else {
			try {
				if (!viewModel.haveEnoughContacts()) {
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
			showInitialFragment(new SetupExplainerFragment());
		}

		viewModel.getState()
				.observe(this, this::onStateChanged);
	}

	private void onStateChanged(SocialBackupSetupViewModel.State state) {
		switch (state) {
			case SUCCESS:
				finish();
				break;
			case FAILURE:
				Toast.makeText(this,
						"There was an error when creating the backup",
						Toast.LENGTH_LONG).show();
				finish();
				break;
			case CHOOSING_CUSTODIANS:
				CustodianSelectorFragment fragment =
						CustodianSelectorFragment.newInstance();
				showNextFragment(fragment);
				break;
		}
	}

	@Override
	public void contactsSelected(Collection<ContactId> contacts) {
		Toast.makeText(this,
				String.format("Selected %d contacts", contacts.size()),
				Toast.LENGTH_SHORT).show();
		viewModel.setCustodians(contacts);
		ThresholdSelectorFragment fragment =
				ThresholdSelectorFragment.newInstance(contacts.size());
		showNextFragment(fragment);
	}

}
