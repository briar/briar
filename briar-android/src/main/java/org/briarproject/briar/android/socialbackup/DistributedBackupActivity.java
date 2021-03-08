package org.briarproject.briar.android.socialbackup;

import android.os.Bundle;
import android.widget.Toast;

import androidx.fragment.app.FragmentTransaction;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.contactselection.ContactSelectorListener;
import org.briarproject.briar.android.fragment.BaseFragment;

import java.util.Collection;

public class DistributedBackupActivity extends BriarActivity implements
		BaseFragment.BaseFragmentListener, ContactSelectorListener,
		ThresholdDefinedListener, ShardsSentDismissedListener {

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_distributed_backup);

		CustodianSelectorFragment fragment =
				CustodianSelectorFragment.newInstance();

		showInitialFragment(fragment);
	}

	@Override
	public void contactsSelected(Collection<ContactId> contacts) {
		Toast.makeText(this,
				String.format("selected %d contacts", contacts.size()),
				Toast.LENGTH_SHORT).show();
		ThresholdSelectorFragment fragment = ThresholdSelectorFragment.newInstance(contacts.size());
		showNextFragment(fragment);
	}

	@Override
	public void thresholdDefined() {
		ShardsSentFragment fragment = new ShardsSentFragment();
		showNextFragment(fragment);
	}

	@Override
	public void shardsSentDismissed() {
		finish();
	}
}
