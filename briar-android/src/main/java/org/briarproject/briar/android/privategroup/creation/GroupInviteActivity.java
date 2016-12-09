package org.briarproject.briar.android.privategroup.creation;

import android.content.Intent;
import android.os.Bundle;

import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.sharing.BaseMessageFragment.MessageFragmentListener;

public class GroupInviteActivity extends BaseGroupInviteActivity
		implements MessageFragmentListener {

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		Intent i = getIntent();
		byte[] g = i.getByteArrayExtra(GROUP_ID);
		if (g == null) throw new IllegalStateException("No GroupId in intent.");
		groupId = new GroupId(g);

		if (bundle == null) {
			showInitialFragment(GroupInviteFragment.newInstance(groupId));
		}
	}

}
