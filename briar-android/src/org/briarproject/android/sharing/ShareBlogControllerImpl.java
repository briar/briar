package org.briarproject.android.sharing;

import org.briarproject.android.contactselection.ContactSelectorControllerImpl;
import org.briarproject.android.contactselection.SelectableContactItem;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.blogs.BlogSharingManager;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;

public class ShareBlogControllerImpl
		extends ContactSelectorControllerImpl<SelectableContactItem>
		implements ShareBlogController {

	private final BlogSharingManager blogSharingManager;

	@Inject
	public ShareBlogControllerImpl(
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			ContactManager contactManager,
			BlogSharingManager blogSharingManager) {
		super(dbExecutor, lifecycleManager, contactManager);
		this.blogSharingManager = blogSharingManager;
	}

	@Override
	protected boolean isSelected(Contact c, boolean wasSelected) {
		return wasSelected;
	}

	@Override
	protected boolean isDisabled(GroupId g, Contact c) throws DbException {
		return !blogSharingManager.canBeShared(g, c);
	}

	@Override
	protected SelectableContactItem getItem(Contact c, boolean selected,
			boolean disabled) {
		return new SelectableContactItem(c, selected, disabled);
	}

	@Override
	public void share(final GroupId g, final Collection<ContactId> contacts,
			final String msg,
			final ResultExceptionHandler<Void, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					for (ContactId c : contacts) {
						blogSharingManager.sendInvitation(g, c, msg);
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
