package org.briarproject.briar.android.sharing;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.api.forum.ForumSharingManager;

import java.util.Collection;

import javax.inject.Inject;

public class ForumSharingStatusActivity extends SharingStatusActivity {

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile ForumSharingManager forumSharingManager;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@DatabaseExecutor
	@Override
	protected Collection<Contact> getSharedWith() throws DbException {
		return forumSharingManager.getSharedWith(getGroupId());
	}

	@DatabaseExecutor
	@Override
	protected Collection<Contact> getSharedBy() throws DbException {
		return forumSharingManager.getSharedBy(getGroupId());
	}

}
