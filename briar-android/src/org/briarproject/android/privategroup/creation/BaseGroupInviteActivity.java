package org.briarproject.android.privategroup.creation;

import org.briarproject.R;
import org.briarproject.android.contactselection.ContactSelectorActivity;
import org.briarproject.android.contactselection.ContactSelectorController;
import org.briarproject.android.contactselection.SelectableContactItem;
import org.briarproject.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.android.sharing.BaseMessageFragment.MessageFragmentListener;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import javax.inject.Inject;

import static org.briarproject.api.privategroup.PrivateGroupConstants.MAX_GROUP_INVITATION_MSG_LENGTH;

public abstract class BaseGroupInviteActivity
		extends ContactSelectorActivity<SelectableContactItem>
		implements MessageFragmentListener {

	@Inject
	CreateGroupController controller;

	@Override
	public ContactSelectorController<SelectableContactItem> getController() {
		return controller;
	}

	@Override
	public void contactsSelected(Collection<ContactId> contacts) {
		super.contactsSelected(contacts);

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
		if (groupId == null)
			throw new IllegalStateException("GroupId was not initialized");
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
