package org.briarproject.android.sharing;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.sync.GroupId;

import javax.inject.Inject;

import static org.briarproject.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;

public class ShareForumActivity extends ShareActivity {

	// Fields that are accessed from background threads must be volatile
	@Inject
	volatile ForumSharingManager forumSharingManager;

	@Override
	BaseMessageFragment getMessageFragment() {
		return ShareForumMessageFragment.newInstance();
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public boolean isDisabled(GroupId groupId, Contact c) throws DbException {
		return !forumSharingManager.canBeShared(groupId, c);
	}

	@Override
	protected void share(GroupId g, ContactId c, String msg)
			throws DbException {
		forumSharingManager.sendInvitation(g, c, msg);
	}

	@Override
	protected int getSharingError() {
		return R.string.forum_share_error;
	}

	@Override
	public int getMaximumMessageLength() {
		return MAX_MESSAGE_BODY_LENGTH;
	}
}
