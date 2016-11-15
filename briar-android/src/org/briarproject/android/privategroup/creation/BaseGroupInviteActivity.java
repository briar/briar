package org.briarproject.android.privategroup.creation;

import org.briarproject.R;
import org.briarproject.android.contactselection.ContactSelectorActivity;
import org.briarproject.android.controller.handler.DestroyableContextManager;
import org.briarproject.android.controller.handler.UiContextExceptionResultHandler;
import org.briarproject.android.sharing.BaseMessageFragment.MessageFragmentListener;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.api.nullsafety.ParametersNotNullByDefault;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import javax.inject.Inject;

import static org.briarproject.api.privategroup.PrivateGroupConstants.MAX_GROUP_INVITATION_MSG_LENGTH;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class BaseGroupInviteActivity
		extends ContactSelectorActivity implements MessageFragmentListener {

	private static final String TAG_GROUP_INVITEE = "briar.GROUP_INVITEE";

	@Inject
	CreateGroupController controller;

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
				new UiContextExceptionResultHandler<Void, DbException>(this,
						TAG_GROUP_INVITEE) {
					@Override
					public void onExceptionUi(DbException exception,
							DestroyableContextManager context) {
						((BaseGroupInviteActivity) context)
								.setResult(RESULT_CANCELED);
						((BaseGroupInviteActivity) context)
								.finish();
					}

					@Override
					public void onResultUi(Void result,
							DestroyableContextManager context) {
						((BaseGroupInviteActivity) context)
								.setResult(RESULT_OK);
						((BaseGroupInviteActivity) context)
								.supportFinishAfterTransition();
					}
				});
		return true;
	}

	@Override
	public int getMaximumMessageLength() {
		return MAX_GROUP_INVITATION_MSG_LENGTH;
	}

}
