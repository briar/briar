package org.briarproject.android.sharing;

import android.os.Bundle;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.controller.handler.UiExceptionHandler;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.api.nullsafety.ParametersNotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import javax.inject.Inject;

import static android.widget.Toast.LENGTH_SHORT;
import static org.briarproject.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ShareForumActivity extends ShareActivity {

	@Inject
	ShareForumController controller;

	@Override
	BaseMessageFragment getMessageFragment() {
		return ShareForumMessageFragment.newInstance();
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle bundle) {
		super.onCreate(bundle);

		if (bundle == null) {
			ShareForumFragment fragment =
					ShareForumFragment.newInstance(groupId);
			getSupportFragmentManager().beginTransaction()
					.add(R.id.fragmentContainer, fragment)
					.commit();
		}
	}

	@Override
	public int getMaximumMessageLength() {
		return MAX_MESSAGE_BODY_LENGTH;
	}

	@Override
	void share(Collection<ContactId> contacts, String msg) {
		controller.share(groupId, contacts, msg,
				new UiExceptionHandler<DbException>(this) {
					@Override
					public void onExceptionUi(DbException exception) {
						// TODO proper error handling
						Toast.makeText(ShareForumActivity.this,
								R.string.forum_share_error, LENGTH_SHORT)
								.show();
					}
				});
	}

}
