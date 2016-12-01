package org.briarproject.briar.android.sharing;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.api.blog.BlogSharingManager;

import java.util.Collection;

import javax.inject.Inject;

public class BlogSharingStatusActivity extends SharingStatusActivity {

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile BlogSharingManager blogSharingManager;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@DatabaseExecutor
	@Override
	protected Collection<Contact> getSharedWith() throws DbException {
		return blogSharingManager.getSharedWith(getGroupId());
	}

	@DatabaseExecutor
	@Override
	protected Collection<Contact> getSharedBy() throws DbException {
		return blogSharingManager.getSharedBy(getGroupId());
	}

}
