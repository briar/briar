package org.briarproject.android.privategroup.creation;

import android.content.Intent;
import android.os.Bundle;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.sharing.BaseMessageFragment.MessageFragmentListener;
import org.briarproject.android.sharing.ContactSelectorFragment;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;

public class GroupInviteActivity extends BaseGroupInviteActivity
		implements MessageFragmentListener {

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		// Initialise the group ID,
		// it will be saved and restored by the superclass
		Intent i = getIntent();
		byte[] g = i.getByteArrayExtra(GROUP_ID);
		if (g == null) throw new IllegalStateException("No GroupId in intent.");
		groupId = new GroupId(g);

		if (bundle == null) {
			ContactSelectorFragment fragment =
					ContactSelectorFragment.newInstance(groupId);
			getSupportFragmentManager().beginTransaction()
					.replace(R.id.fragmentContainer, fragment)
					.commit();
		}
	}

	@Override
	@DatabaseExecutor
	public boolean isDisabled(GroupId groupId, Contact c) throws DbException {
		// TODO disable contacts that can not be invited
		return false;
	}

}
