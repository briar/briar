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
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.android.contactselection.ContactSelectorControllerImpl;
import org.briarproject.briar.android.controller.handler.ExceptionHandler;
import org.briarproject.briar.api.blog.BlogSharingManager;
import org.briarproject.briar.api.messaging.ConversationManager;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;

@Immutable
@NotNullByDefault
class ShareBlogControllerImpl extends ContactSelectorControllerImpl
		implements ShareBlogController {

	private final static Logger LOG =
			Logger.getLogger(ShareBlogControllerImpl.class.getName());

	private final ConversationManager conversationManager;
	private final BlogSharingManager blogSharingManager;
	private final Clock clock;

	@Inject
	ShareBlogControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, ContactManager contactManager,
			ConversationManager conversationManager,
			BlogSharingManager blogSharingManager, Clock clock) {
		super(dbExecutor, lifecycleManager, contactManager);
		this.conversationManager = conversationManager;
		this.blogSharingManager = blogSharingManager;
		this.clock = clock;
	}

	@Override
	protected boolean isDisabled(GroupId g, Contact c) throws DbException {
		return !blogSharingManager.canBeShared(g, c);
	}

	@Override
	public void share(final GroupId g, final Collection<ContactId> contacts,
			final String message,
			final ExceptionHandler<DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					String msg = isNullOrEmpty(message) ? null : message;
					for (ContactId c : contacts) {
						try {
							long time = Math.max(clock.currentTimeMillis(),
									conversationManager.getGroupCount(c)
											.getLatestMsgTime() + 1);
							blogSharingManager.sendInvitation(g, c, msg, time);
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
