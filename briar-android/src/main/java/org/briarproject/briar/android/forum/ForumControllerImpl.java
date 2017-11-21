package org.briarproject.briar.android.forum;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;
import org.briarproject.briar.android.forum.ForumController.ForumListener;
import org.briarproject.briar.android.threaded.ThreadListControllerImpl;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.forum.ForumInvitationResponse;
import org.briarproject.briar.api.forum.ForumManager;
import org.briarproject.briar.api.forum.ForumPost;
import org.briarproject.briar.api.forum.ForumPostHeader;
import org.briarproject.briar.api.forum.ForumSharingManager;
import org.briarproject.briar.api.forum.event.ForumInvitationResponseReceivedEvent;
import org.briarproject.briar.api.forum.event.ForumPostReceivedEvent;
import org.briarproject.briar.api.sharing.event.ContactLeftShareableEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static java.lang.Math.max;
import static java.util.logging.Level.WARNING;

@NotNullByDefault
class ForumControllerImpl extends
		ThreadListControllerImpl<Forum, ForumItem, ForumPostHeader, ForumPost, ForumListener>
		implements ForumController {

	private static final Logger LOG =
			Logger.getLogger(ForumControllerImpl.class.getName());

	private final ForumManager forumManager;
	private final ForumSharingManager forumSharingManager;

	@Inject
	ForumControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, IdentityManager identityManager,
			@CryptoExecutor Executor cryptoExecutor,
			ForumManager forumManager, ForumSharingManager forumSharingManager,
			EventBus eventBus, Clock clock, MessageTracker messageTracker,
			AndroidNotificationManager notificationManager) {
		super(dbExecutor, lifecycleManager, identityManager, cryptoExecutor,
				eventBus, clock, notificationManager, messageTracker);
		this.forumManager = forumManager;
		this.forumSharingManager = forumSharingManager;
	}

	@Override
	public void onActivityStart() {
		super.onActivityStart();
		notificationManager.clearForumPostNotification(getGroupId());
	}

	@Override
	public void eventOccurred(Event e) {
		super.eventOccurred(e);

		if (e instanceof ForumPostReceivedEvent) {
			ForumPostReceivedEvent f = (ForumPostReceivedEvent) e;
			if (f.getGroupId().equals(getGroupId())) {
				LOG.info("Forum post received, adding...");
				onForumPostReceived(f.getHeader(), f.getBody());
			}
		} else if (e instanceof ForumInvitationResponseReceivedEvent) {
			ForumInvitationResponseReceivedEvent f =
					(ForumInvitationResponseReceivedEvent) e;
			ForumInvitationResponse r =
					(ForumInvitationResponse) f.getResponse();
			if (r.getShareableId().equals(getGroupId()) && r.wasAccepted()) {
				LOG.info("Forum invitation was accepted");
				onForumInvitationAccepted(r.getContactId());
			}
		} else if (e instanceof ContactLeftShareableEvent) {
			ContactLeftShareableEvent c = (ContactLeftShareableEvent) e;
			if (c.getGroupId().equals(getGroupId())) {
				LOG.info("Forum left by contact");
				onForumLeft(c.getContactId());
			}
		}
	}

	@Override
	protected Forum loadNamedGroup() throws DbException {
		return forumManager.getForum(getGroupId());
	}

	@Override
	protected Collection<ForumPostHeader> loadHeaders() throws DbException {
		return forumManager.getPostHeaders(getGroupId());
	}

	@Override
	protected String loadMessageBody(ForumPostHeader h) throws DbException {
		return forumManager.getPostBody(h.getId());
	}

	@Override
	protected void markRead(MessageId id) throws DbException {
		forumManager.setReadFlag(getGroupId(), id, true);
	}

	@Override
	public void loadSharingContacts(
			ResultExceptionHandler<Collection<ContactId>, DbException> handler) {
		runOnDbThread(() -> {
			try {
				Collection<Contact> contacts =
						forumSharingManager.getSharedWith(getGroupId());
				Collection<ContactId> contactIds =
						new ArrayList<>(contacts.size());
				for (Contact c : contacts) contactIds.add(c.getId());
				handler.onResult(contactIds);
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				handler.onException(e);
			}
		});
	}

	@Override
	public void createAndStoreMessage(String body,
			@Nullable ForumItem parentItem,
			ResultExceptionHandler<ForumItem, DbException> handler) {
		runOnDbThread(() -> {
			try {
				LocalAuthor author = identityManager.getLocalAuthor();
				GroupCount count = forumManager.getGroupCount(getGroupId());
				long timestamp = max(count.getLatestMsgTime() + 1,
						clock.currentTimeMillis());
				MessageId parentId = parentItem != null ?
						parentItem.getId() : null;
				createMessage(body, timestamp, parentId, author, handler);
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				handler.onException(e);
			}
		});
	}

	private void createMessage(String body, long timestamp,
			@Nullable MessageId parentId, LocalAuthor author,
			ResultExceptionHandler<ForumItem, DbException> handler) {
		cryptoExecutor.execute(() -> {
			LOG.info("Creating forum post...");
			ForumPost msg = forumManager.createLocalPost(getGroupId(), body,
					timestamp, parentId, author);
			storePost(msg, body, handler);
		});
	}

	@Override
	protected ForumPostHeader addLocalMessage(ForumPost p)
			throws DbException {
		return forumManager.addLocalPost(p);
	}

	@Override
	protected void deleteNamedGroup(Forum forum) throws DbException {
		forumManager.removeForum(forum);
	}

	@Override
	protected ForumItem buildItem(ForumPostHeader header, String body) {
		return new ForumItem(header, body);
	}

	private void onForumPostReceived(ForumPostHeader h, String body) {
		ForumItem item = buildItem(h, body);
		listener.runOnUiThreadUnlessDestroyed(
				() -> listener.onItemReceived(item));
	}

	private void onForumInvitationAccepted(ContactId c) {
		listener.runOnUiThreadUnlessDestroyed(
				() -> listener.onInvitationAccepted(c));
	}

	private void onForumLeft(ContactId c) {
		listener.runOnUiThreadUnlessDestroyed(() -> listener.onForumLeft(c));
	}

}
