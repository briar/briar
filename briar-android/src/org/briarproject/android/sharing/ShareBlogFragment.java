package org.briarproject.android.sharing;

import android.os.Bundle;

import org.briarproject.android.ActivityComponent;
import org.briarproject.android.contactselection.ContactSelectorController;
import org.briarproject.android.contactselection.ContactSelectorFragment;
import org.briarproject.android.contactselection.SelectableContactItem;
import org.briarproject.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.api.sync.GroupId;

import javax.inject.Inject;

import static org.briarproject.api.sharing.SharingConstants.GROUP_ID;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ShareBlogFragment extends ContactSelectorFragment {

	public static final String TAG = ShareBlogFragment.class.getName();

	@Inject
	ShareBlogController controller;

	public static ShareBlogFragment newInstance(GroupId groupId) {
		Bundle args = new Bundle();
		args.putByteArray(GROUP_ID, groupId.getBytes());
		ShareBlogFragment fragment = new ShareBlogFragment();
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
