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

//		viewModel.getState().observeEvent(this, this::onStateChanged);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_distributed_backup);
        if (viewModel.remoteWipeIsSetup()) {
//        	showInitialFragment();
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

}
