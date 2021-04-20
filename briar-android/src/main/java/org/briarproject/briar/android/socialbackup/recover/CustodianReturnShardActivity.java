package org.briarproject.briar.android.socialbackup.recover;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.api.socialbackup.recovery.CustodianTask;

import java.io.IOException;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import static java.util.logging.Logger.getLogger;
import static org.briarproject.briar.android.conversation.ConversationActivity.CONTACT_ID;

public class CustodianReturnShardActivity extends BriarActivity
		implements BaseFragment.BaseFragmentListener {

	private CustodianReturnShardViewModel viewModel;
	private static final Logger LOG =
			getLogger(CustodianReturnShardActivity.class.getName());

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(CustodianReturnShardViewModel.class);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_fragment_container);
		if (state == null) {
			Intent intent = getIntent();
			int id = intent.getIntExtra(CONTACT_ID, -1);
			if (id == -1) throw new IllegalStateException("No ContactId");
			ContactId contactId = new ContactId(id);

			try {
				viewModel.start(contactId);
			} catch (IOException e) {
				// TODO improve this
				Toast.makeText(this,
						"It looks like you are not connected to a Wifi network",
						Toast.LENGTH_SHORT).show();
			} catch (DbException e) {
				Toast.makeText(this,
						"You do not hold a backup piece for this contact",
						Toast.LENGTH_SHORT).show();
				finish();
			}
			showInitialFragment(new CustodianRecoveryModeExplainerFragment());
		}
		viewModel.getShowCameraFragment().observeEvent(this, show -> {
			if (show) showCameraFragment();
		});
		viewModel.getSuccessDismissed().observeEvent(this, dismissed -> {
			if (dismissed) finish();
		});
		viewModel.getState()
				.observe(this, this::onReturnShardStateChanged);
	}

	private void onReturnShardStateChanged(CustodianTask.State state) {
        if (state instanceof CustodianTask.State.Success) {
	        CustodianReturnShardSuccessFragment fragment = new CustodianReturnShardSuccessFragment();
	        showNextFragment(fragment);
        } else if (state instanceof CustodianTask.State.Failure) {
        	// TODO error fragment here
	        // TODO handle reason
	        Toast.makeText(this,
			        "Backup piece transfer failed",
			        Toast.LENGTH_SHORT).show();
	        finish();
        }
	}

	private void showCameraFragment() {
		// FIXME #824
		FragmentManager fm = getSupportFragmentManager();
		if (fm.findFragmentByTag(CustodianReturnShardFragment.TAG) == null) {
			BaseFragment f = CustodianReturnShardFragment.newInstance();
			fm.beginTransaction()
					.replace(R.id.fragmentContainer, f, f.getUniqueTag())
					.addToBackStack(f.getUniqueTag())
					.commit();
		}
	}
}
