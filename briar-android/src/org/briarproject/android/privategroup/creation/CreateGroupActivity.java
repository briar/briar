package org.briarproject.android.privategroup.creation;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.android.privategroup.conversation.GroupActivity;
import org.briarproject.android.sharing.BaseMessageFragment.MessageFragmentListener;
import org.briarproject.android.sharing.ContactSelectorActivity;
import org.briarproject.android.sharing.ContactSelectorFragment;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import javax.inject.Inject;

import static android.support.v4.app.ActivityOptionsCompat.makeCustomAnimation;
import static android.widget.Toast.LENGTH_SHORT;
import static org.briarproject.api.privategroup.PrivateGroupConstants.MAX_GROUP_POST_BODY_LENGTH;

public class CreateGroupActivity extends ContactSelectorActivity implements
		CreateGroupListener, MessageFragmentListener {

	@Inject
	CreateGroupController controller;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		setContentView(R.layout.activity_fragment_container);

		if (bundle == null) {
			CreateGroupFragment fragment = new CreateGroupFragment();
			getSupportFragmentManager().beginTransaction()
					.add(R.id.fragmentContainer, fragment)
					.commit();
		} else {
			byte[] groupBytes = bundle.getByteArray(GROUP_ID);
			if (groupBytes != null) groupId = new GroupId(groupBytes);
		}
	}

	@Override
	public void onBackPressed() {
		if (getSupportFragmentManager().getBackStackEntryCount() == 1) {
			// At this point, the group had been created already,
			// so don't allow to create it again.
			openNewGroup();
		} else {
			super.onBackPressed();
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
	public void onGroupNameChosen(String name) {
		controller.createGroup(name,
				new UiResultExceptionHandler<GroupId, DbException>(this) {
					@Override
					public void onResultUi(GroupId g) {
						switchToContactSelectorFragment(g);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO proper error handling
						finish();
					}
				});
	}

	private void switchToContactSelectorFragment(GroupId g) {
		ContactSelectorFragment fragment =
				ContactSelectorFragment.newInstance(g);
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
	public boolean isDisabled(GroupId groupId, Contact c) throws DbException {
		return false;
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
						Toast.makeText(CreateGroupActivity.this,
								"Inviting members is not yet implemented",
								LENGTH_SHORT).show();
						openNewGroup();
					}

					@Override
					public void onExceptionUi(DbException exception) {
						// TODO proper error handling
						finish();
					}
				});
		return true;
	}

	@Override
	public int getMaximumMessageLength() {
		return MAX_GROUP_POST_BODY_LENGTH;
	}

	private void openNewGroup() {
		Intent i = new Intent(this, GroupActivity.class);
		i.putExtra(GROUP_ID, groupId.getBytes());
		ActivityOptionsCompat options =
				makeCustomAnimation(this, android.R.anim.fade_in,
						android.R.anim.fade_out);
		ActivityCompat.startActivity(this, i, options.toBundle());
		// finish this activity, so we can't come back to it
		finish();
	}

}
