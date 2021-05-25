package org.briarproject.briar.android.remotewipe.activate;

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

public class ActivateRemoteWipeActivity extends BriarActivity implements
		BaseFragment.BaseFragmentListener {

	@Inject
	ViewModelProvider.Factory viewModelFactory;
	ActivateRemoteWipeViewModel viewModel;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);

		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(ActivateRemoteWipeViewModel.class);

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
			viewModel.setContactId(contactId);
			showInitialFragment(new ActivateRemoteWipeExplainerFragment());
		}
	}

	private void onStateChanged(ActivateRemoteWipeState state) {
	   switch(state) {
		   case FAILED:
			   Toast.makeText(this,
					   R.string.remote_wipe_activate_failure,
					   Toast.LENGTH_LONG).show();
			   break;
		   case SUCCESS:
//			   showNextFragment(new ActivateRemoteWipeSuccessFragment());
			   Toast.makeText(this,
					   R.string.remote_wipe_activate_success,
					   Toast.LENGTH_LONG).show();
			   finish();
			   break;
		   case FINISHED:
			   finish();
			   break;
		   case CANCELLED:
			   finish();
			   break;
	   }
	}
}
