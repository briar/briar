package org.briarproject.android.privategroup.creation;

import android.content.Context;
import android.os.Bundle;

import org.briarproject.android.ActivityComponent;
import org.briarproject.android.contact.BaseContactListAdapter;
import org.briarproject.android.contactselection.ContactSelectorAdapter;
import org.briarproject.android.contactselection.ContactSelectorController;
import org.briarproject.android.contactselection.ContactSelectorFragment;
import org.briarproject.android.contactselection.SelectableContactItem;
import org.briarproject.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.api.sync.GroupId;

import javax.inject.Inject;

import static org.briarproject.android.BriarActivity.GROUP_ID;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class GroupInviteFragment extends ContactSelectorFragment {

	public static final String TAG = GroupInviteFragment.class.getName();

	@Inject
	CreateGroupController controller;

	public static GroupInviteFragment newInstance(GroupId groupId) {
		Bundle args = new Bundle();
		args.putByteArray(GROUP_ID, groupId.getBytes());
		GroupInviteFragment fragment = new GroupInviteFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	protected ContactSelectorController<SelectableContactItem> getController() {
		return controller;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

}
