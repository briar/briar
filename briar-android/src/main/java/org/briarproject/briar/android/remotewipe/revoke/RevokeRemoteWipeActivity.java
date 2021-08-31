package org.briarproject.briar.android.remotewipe.revoke;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment;

import javax.inject.Inject;

import androidx.lifecycle.ViewModelProvider;

import static org.briarproject.briar.android.conversation.ConversationActivity.CONTACT_ID;

public class RevokeRemoteWipeActivity extends BriarActivity implements
		BaseFragment.BaseFragmentListener {

	@Inject
	ViewModelProvider.Factory viewModelFactory;
	RevokeRemoteWipeViewModel viewModel;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);

		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(RevokeRemoteWipeViewModel.class);

		viewModel.getState().observe(this, this::onStateChanged);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_distributed_backup);

		if (savedInstanceState == null) {
			Intent intent = getIntent();
			int id = intent.getIntExtra(CONTACT_ID, -1);
			if (id == -1) throw new IllegalStateException("No ContactId");
			ContactId contactId = new ContactId(id);
			viewModel.revokeRemoteWipeStatus(contactId);
//			showInitialFragment(new ActivateRemoteWipeExplainerFragment());
		}
	}

	private void onStateChanged(RevokeRemoteWipeState state) {
		switch(state) {
			case FAILED:
				// TODO change text
				Toast.makeText(this,
						R.string.remote_wipe_activate_failure,
						Toast.LENGTH_LONG).show();
				break;
			case SUCCESS:
				showNextFragment(new RevokeRemoteWipeSuccessFragment());
				break;
			default: // FINISHED or CANCELLED
				finish();
				break;
		}
	}
}
