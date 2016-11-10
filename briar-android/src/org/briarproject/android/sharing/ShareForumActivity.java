package org.briarproject.android.sharing;

import android.os.Bundle;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.contactselection.ContactSelectorController;
import org.briarproject.android.contactselection.SelectableContactItem;
import org.briarproject.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;

import java.util.Collection;

import javax.inject.Inject;

import static android.widget.Toast.LENGTH_SHORT;
import static org.briarproject.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;

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
	public ContactSelectorController<SelectableContactItem> getController() {
		return controller;
	}

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
	}

	@Override
	public int getMaximumMessageLength() {
		return MAX_MESSAGE_BODY_LENGTH;
	}

	@Override
	void share(Collection<ContactId> contacts, String msg) {
		controller.share(groupId, contacts, msg,
				new UiResultExceptionHandler<Void, DbException>(this) {
					@Override
					public void onResultUi(Void result) {

					}

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
