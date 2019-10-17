package org.briarproject.briar.android.privategroup.creation;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.sharing.BaseMessageFragment;

import androidx.annotation.StringRes;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class CreateGroupMessageFragment extends BaseMessageFragment {

	private final static String TAG =
			CreateGroupMessageFragment.class.getName();

	@Override
	@StringRes
	protected int getButtonText() {
		return R.string.groups_create_group_invitation_button;
	}

	@Override
	@StringRes
	protected int getHintText() {
		return R.string.forum_share_message;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

}
