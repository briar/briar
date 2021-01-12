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
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.android.controller.ActivityLifecycleController;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.blog.BlogSharingManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class BlogControllerImpl extends BaseControllerImpl
		implements ActivityLifecycleController, BlogController, EventListener {

	private static final Logger LOG =
			Logger.getLogger(BlogControllerImpl.class.getName());

	private final BlogSharingManager blogSharingManager;

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
	}

	@Override
	public void onActivityStop() {
	}

	@Override
	public void onActivityDestroy() {
	}

	@Override
	public void setGroupId(GroupId g) {
		groupId = g;
	}

	@Override
	public void eventOccurred(Event e) {

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
