package org.briarproject.briar.android.socialbackup;

import android.os.Bundle;
import android.widget.Toast;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.contactselection.ContactSelectorListener;
import org.briarproject.briar.android.fragment.BaseFragment;

import java.util.Collection;

public class OldDistributedBackupActivity extends BriarActivity
		implements BaseFragment.BaseFragmentListener, ContactSelectorListener {

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_distributed_backup);

//		CustodianDisplayFragment fragment =
//				CustodianDisplayFragment.newInstance();
//
//		showInitialFragment(fragment);
	}

	@Override
	public void contactsSelected(Collection<ContactId> contacts) {
		// do nothing
	}

}
