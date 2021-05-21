package org.briarproject.briar.android.remotewipe;

import android.os.Bundle;
import android.widget.Toast;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.contactselection.ContactSelectorListener;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.socialbackup.ThresholdSelectorFragment;

import java.util.Collection;

import javax.inject.Inject;

import androidx.lifecycle.ViewModelProvider;

public class RemoteWipeSetupActivity extends BriarActivity implements
		BaseFragment.BaseFragmentListener, ContactSelectorListener {

	@Inject
	ViewModelProvider.Factory viewModelFactory;
	RemoteWipeSetupViewModel viewModel;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);

		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(RemoteWipeSetupViewModel.class);

		viewModel.getState().observe(this, this::onStateChanged);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_distributed_backup);
        if (viewModel.remoteWipeIsSetup()) {
        	showInitialFragment(new RemoteWipeDisplayFragment());
        } else {
	        showInitialFragment(WiperSelectorFragment.newInstance());
        }

	}

	@Override
	public void contactsSelected(Collection<ContactId> contacts) {
		Toast.makeText(this,
				String.format("Selected %d contacts", contacts.size()),
				Toast.LENGTH_SHORT).show();
		try {
			viewModel.setupRemoteWipe(contacts);
		} catch (Exception e) {
			// Display error fragment
		}
	}

	private void onStateChanged(RemoteWipeSetupState state) {
		if (state.equals(RemoteWipeSetupState.SUCCESS)) {
			showNextFragment(new RemoteWipeSuccessFragment());
		} else if (state.equals(RemoteWipeSetupState.FAILED)) {
			Toast.makeText(this,
					R.string.remote_wipe_setup_failed,
					Toast.LENGTH_LONG).show();
			finish();
		} else if (state.equals(RemoteWipeSetupState.FINISHED)) {
			finish();
		}
	}
}
