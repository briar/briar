package org.briarproject.briar.android.conversation;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;

import javax.inject.Inject;

import androidx.appcompat.app.ActionBar;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;

import static org.briarproject.briar.android.conversation.ConversationActivity.CONTACT_ID;

public class ConversationSettingsActivity extends BriarActivity implements
		BaseFragmentListener {

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private ConversationViewModel viewModel;

	private ContactId contactId;

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		Intent i = getIntent();
		int id = i.getIntExtra(CONTACT_ID, -1);
		if (id == -1) throw new IllegalStateException();
		contactId = new ContactId(id);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setHomeButtonEnabled(true);
			actionBar.setDisplayHomeAsUpEnabled(true);
		}

		setContentView(R.layout.activity_conversation_settings);

		viewModel = ViewModelProviders.of(this, viewModelFactory)
				.get(ConversationViewModel.class);
	}

	@Override
	public void onResume() {
		super.onResume();
		// Trigger loading of contact data, noop if data was loaded already.
		//
		// We can only start loading data *after* we are sure
		// the user has signed in. After sign-in, onCreate() isn't run again.
		if (signedIn()) viewModel.setContactId(contactId);
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return false;
	}

}
