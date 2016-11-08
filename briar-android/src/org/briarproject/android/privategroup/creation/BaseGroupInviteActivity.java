package org.briarproject.android.privategroup.creation;

import android.os.Bundle;

import org.briarproject.R;
import org.briarproject.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.android.sharing.BaseMessageFragment.MessageFragmentListener;
import org.briarproject.android.sharing.ContactSelectorActivity;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import javax.inject.Inject;

import static org.briarproject.api.privategroup.PrivateGroupConstants.MAX_GROUP_INVITATION_MSG_LENGTH;

public abstract class BaseGroupInviteActivity
		extends ContactSelectorActivity
		implements MessageFragmentListener {

	@Inject
	CreateGroupController controller;

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		// Subclasses may initialise the group ID in different places,
		// restore it if it was saved
		if (bundle != null) {
			byte[] groupBytes = bundle.getByteArray(GROUP_ID);
			if (groupBytes != null) groupId = new GroupId(groupBytes);
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (groupId != null) {
			outState.putByteArray(GROUP_ID, groupId.getBytes());
		}
	}

	@Override
	public void contactsSelected(GroupId groupId,
			Collection<ContactId> contacts) {
		super.contactsSelected(groupId, contacts);

		CreateGroupMessageFragment fragment = new CreateGroupMessageFragment();
		getSupportFragmentManager().beginTransaction()
				.setCustomAnimations(android.R.anim.fade_in,
						android.R.anim.fade_out,
						android.R.anim.slide_in_left,
						android.R.anim.slide_out_right)
				.replace(R.id.fragmentContainer, fragment)
				.addToBackStack(fragment.getUniqueTag())
				.commit();
	}

	@Override
	public boolean onButtonClick(@NotNull String message) {
		controller.sendInvitation(groupId, contacts, message,
				new UiResultExceptionHandler<Void, DbException>(this) {
					@Override
					public void onResultUi(Void result) {
						setResult(RESULT_OK);
						supportFinishAfterTransition();
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO proper error handling
						setResult(RESULT_CANCELED);
						finish();
					}
				});
		return true;
	}

	@Override
	public int getMaximumMessageLength() {
		return MAX_GROUP_INVITATION_MSG_LENGTH;
	}

}
