package org.briarproject.briar.android.blog;

import android.app.Activity;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.event.GroupRemovedEvent;
import org.briarproject.briar.android.controller.ActivityLifecycleController;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogInvitationResponse;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.blog.BlogSharingManager;
import org.briarproject.briar.api.blog.event.BlogInvitationResponseReceivedEvent;
import org.briarproject.briar.api.blog.event.BlogPostAddedEvent;
import org.briarproject.briar.api.sharing.event.ContactLeftShareableEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.Nullable;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class BlogControllerImpl extends BaseControllerImpl
		implements ActivityLifecycleController, BlogController, EventListener {

	private static final Logger LOG =
			Logger.getLogger(BlogControllerImpl.class.getName());

	private final BlogSharingManager blogSharingManager;

	// UI thread
	@Nullable
	private BlogSharingListener listener;

	private volatile GroupId groupId = null;

	@Inject
	BlogControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, EventBus eventBus,
			AndroidNotificationManager notificationManager,
			IdentityManager identityManager, BlogManager blogManager,
			BlogSharingManager blogSharingManager) {
		super(dbExecutor, lifecycleManager, eventBus, notificationManager,
				identityManager, blogManager);
		this.blogSharingManager = blogSharingManager;
	}

	@Override
	public void onActivityCreate(Activity activity) {
	}

	@Override
	public void onActivityStart() {
		super.onStart();
		notificationManager.blockNotification(groupId);
		notificationManager.clearBlogPostNotification(groupId);
	}

	@Override
	public void onActivityStop() {
		super.onStop();
		notificationManager.unblockNotification(groupId);
	}

	@Override
	public void onActivityDestroy() {
	}

	@Override
	public void setGroupId(GroupId g) {
		groupId = g;
	}

	@Override
	public void setBlogSharingListener(BlogSharingListener listener) {
		this.listener = listener;
	}

	@Override
	public void unsetBlogSharingListener(BlogSharingListener listener) {
		if (this.listener == listener) this.listener = null;
	}

	@Override
	public void eventOccurred(Event e) {
		if (groupId == null || listener == null)
			throw new IllegalStateException();
		if (e instanceof BlogPostAddedEvent) {
			BlogPostAddedEvent b = (BlogPostAddedEvent) e;
			if (b.getGroupId().equals(groupId)) {
				LOG.info("Blog post added");
				listener.onBlogPostAdded(b.getHeader(), b.isLocal());
			}
		} else if (e instanceof BlogInvitationResponseReceivedEvent) {
			BlogInvitationResponseReceivedEvent b =
					(BlogInvitationResponseReceivedEvent) e;
			BlogInvitationResponse r = b.getMessageHeader();
			if (r.getShareableId().equals(groupId) && r.wasAccepted()) {
				LOG.info("Blog invitation accepted");
				listener.onBlogInvitationAccepted(b.getContactId());
			}
		} else if (e instanceof ContactLeftShareableEvent) {
			ContactLeftShareableEvent s = (ContactLeftShareableEvent) e;
			if (s.getGroupId().equals(groupId)) {
				LOG.info("Blog left by contact");
				listener.onBlogLeft(s.getContactId());
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent g = (GroupRemovedEvent) e;
			if (g.getGroup().getId().equals(groupId)) {
				LOG.info("Blog removed");
				listener.onBlogRemoved();
			}
		}
	}

	@Override
	public void loadBlogPosts(
			ResultExceptionHandler<List<BlogPostItem>, DbException> handler) {
		if (groupId == null) throw new IllegalStateException();
		loadBlogPosts(groupId, handler);
	}

	@Override
	public void loadBlogPost(MessageId m,
			ResultExceptionHandler<BlogPostItem, DbException> handler) {
		if (groupId == null) throw new IllegalStateException();
		loadBlogPost(groupId, m, handler);
	}

	@Override
	public void loadBlog(
			ResultExceptionHandler<BlogItem, DbException> handler) {
		if (groupId == null) throw new IllegalStateException();
		runOnDbThread(() -> {
			try {
				long start = now();
				LocalAuthor a = identityManager.getLocalAuthor();
				Blog b = blogManager.getBlog(groupId);
				boolean ours = a.getId().equals(b.getAuthor().getId());
				boolean removable = blogManager.canBeRemoved(b);
				BlogItem blog = new BlogItem(b, ours, removable);
				logDuration(LOG, "Loading blog", start);
				handler.onResult(blog);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				handler.onException(e);
			}
		});
	}

	@Override
	public void deleteBlog(ResultExceptionHandler<Void, DbException> handler) {
		if (groupId == null) throw new IllegalStateException();
		runOnDbThread(() -> {
			try {
				long start = now();
				Blog b = blogManager.getBlog(groupId);
				blogManager.removeBlog(b);
				logDuration(LOG, "Removing blog", start);
				handler.onResult(null);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				handler.onException(e);
			}
		});
	}

	@Override
	public void loadSharingContacts(
			ResultExceptionHandler<Collection<ContactId>, DbException> handler) {
		if (groupId == null) throw new IllegalStateException();
		runOnDbThread(() -> {
			try {
				Collection<Contact> contacts =
						blogSharingManager.getSharedWith(groupId);
				Collection<ContactId> contactIds =
						new ArrayList<>(contacts.size());
				for (Contact c : contacts) contactIds.add(c.getId());
				handler.onResult(contactIds);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				handler.onException(e);
			}
		});
	}

}
