package org.briarproject.android.sharing;

import org.briarproject.android.ActivityComponent;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.db.DbException;
import org.briarproject.api.forum.ForumSharingManager;

import java.util.Collection;

import javax.inject.Inject;

public class SharingStatusForumActivity extends SharingStatusActivity {

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile ForumSharingManager forumSharingManager;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	/**
	 * This must only be called from the DbThread
	 */
	protected Collection<Contact> getSharedWith() throws DbException {
		return forumSharingManager.getSharedWith(getGroupId());
	}

	/**
	 * This must only be called from the DbThread
	 */
	protected Collection<Contact> getSharedBy() throws DbException {
		return forumSharingManager.getSharedBy(getGroupId());
	}

}
