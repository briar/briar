package org.briarproject.briar.android.sharing;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.NoSuchContactException;
import org.briarproject.bramble.api.db.NoSuchGroupException;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.android.contactselection.ContactSelectorControllerImpl;
import org.briarproject.briar.android.controller.handler.ExceptionHandler;
import org.briarproject.briar.api.blog.BlogSharingManager;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static java.util.logging.Level.WARNING;

@Immutable
@NotNullByDefault
class ShareBlogControllerImpl extends ContactSelectorControllerImpl
		implements ShareBlogController {

	private final static Logger LOG =
			Logger.getLogger(ShareBlogControllerImpl.class.getName());

	private final BlogSharingManager blogSharingManager;

	@Inject
	ShareBlogControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, ContactManager contactManager,
			BlogSharingManager blogSharingManager) {
		super(dbExecutor, lifecycleManager, contactManager);
		this.blogSharingManager = blogSharingManager;
	}

	@Override
	protected boolean isDisabled(GroupId g, Contact c) throws DbException {
		return !blogSharingManager.canBeShared(g, c);
	}

	@Override
	public void share(final GroupId g, final Collection<ContactId> contacts,
			final String msg,
			final ExceptionHandler<DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					for (ContactId c : contacts) {
						try {
							blogSharingManager.sendInvitation(g, c, msg);
						} catch (NoSuchContactException | NoSuchGroupException e) {
							if (LOG.isLoggable(WARNING))
								LOG.log(WARNING, e.toString(), e);
						}
					}
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

}
