package org.briarproject.android.sharing;

import org.briarproject.android.ActivityComponent;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;

import javax.inject.Inject;

public class ShareForumActivity extends ShareActivity {
	@Inject
	volatile ForumSharingManager forumSharingManager;

	ShareMessageFragment getMessageFragment(GroupId groupId,
			Collection<ContactId> contacts) {
		return ShareForumMessageFragment.newInstance(groupId, contacts);
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	/**
	 * This must only be called from a DbThread
	 */
	boolean isDisabled(GroupId groupId, Contact c) throws DbException {
		return !forumSharingManager.canBeShared(groupId, c);
	}
}
