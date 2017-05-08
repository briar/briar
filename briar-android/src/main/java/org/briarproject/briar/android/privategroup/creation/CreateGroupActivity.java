package org.briarproject.briar.android.privategroup.creation;

import android.content.Intent;
import android.os.Bundle;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.briar.android.privategroup.conversation.GroupActivity;
import org.briarproject.briar.android.sharing.BaseMessageFragment.MessageFragmentListener;

import javax.annotation.Nullable;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class CreateGroupActivity extends BaseGroupInviteActivity implements
		CreateGroupListener, MessageFragmentListener {

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle bundle) {
		super.onCreate(bundle);

		if (bundle == null) {
			showInitialFragment(new CreateGroupFragment());
		}
	}

	@Override
	public void onBackPressed() {
		if (getSupportFragmentManager().getBackStackEntryCount() == 1) {
			// At this point, the group had been created already,
			// so don't allow to create it again.
			openNewGroup();
			overridePendingTransition(R.anim.screen_old_in,
					R.anim.screen_new_out);
		} else {
			super.onBackPressed();
		}
	}

	@Override
	public void onGroupNameChosen(String name) {
		controller.createGroup(name,
				new UiResultExceptionHandler<GroupId, DbException>(this) {
					@Override
					public void onResultUi(GroupId g) {
						groupId = g;
						switchToContactSelectorFragment(g);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						handleDbException(exception);
					}
				});
	}

	private void switchToContactSelectorFragment(GroupId g) {
		showNextFragment(GroupInviteFragment.newInstance(g));
	}

	private void openNewGroup() {
		Intent i = new Intent(this, GroupActivity.class);
		i.putExtra(GROUP_ID, groupId.getBytes());
		startActivity(i);
		// finish this activity, so we can't come back to it
		finish();
	}

}
