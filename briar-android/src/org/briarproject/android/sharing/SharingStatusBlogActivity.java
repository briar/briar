package org.briarproject.android.sharing;

import org.briarproject.android.ActivityComponent;
import org.briarproject.api.blogs.BlogSharingManager;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.db.DbException;

import java.util.Collection;

import javax.inject.Inject;

public class SharingStatusBlogActivity extends SharingStatusActivity {

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile BlogSharingManager blogSharingManager;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	/**
	 * This must only be called from the DbThread
	 */
	protected Collection<Contact> getSharedWith() throws DbException {
		return blogSharingManager.getSharedWith(getGroupId());
	}

	/**
	 * This must only be called from the DbThread
	 */
	protected Collection<Contact> getSharedBy() throws DbException {
		return blogSharingManager.getSharedBy(getGroupId());
	}

}
