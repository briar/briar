package org.briarproject.briar.android.forum;

import android.app.Application;
import android.widget.Toast;

import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.R;
import org.briarproject.briar.android.threaded.ThreadListViewModel;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;
import org.briarproject.briar.api.client.PostHeader;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.forum.ForumManager;
import org.briarproject.briar.api.forum.ForumPost;
import org.briarproject.briar.api.forum.ForumPostHeader;
import org.briarproject.briar.api.forum.ForumSharingManager;
import org.briarproject.briar.api.forum.event.ForumPostReceivedEvent;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static android.widget.Toast.LENGTH_SHORT;
import static java.lang.Math.max;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class ForumViewModel extends ThreadListViewModel<ForumPostItem> {

	private static final Logger LOG = getLogger(ForumViewModel.class.getName());

	private final ForumManager forumManager;
	private final ForumSharingManager forumSharingManager;

	@Inject
	ForumViewModel(Application application,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			TransactionManager db,
			AndroidExecutor androidExecutor,
			IdentityManager identityManager,
			AndroidNotificationManager notificationManager,
			@CryptoExecutor Executor cryptoExecutor,
			Clock clock,
			MessageTracker messageTracker,
			EventBus eventBus,
			ForumManager forumManager,
			ForumSharingManager forumSharingManager) {
		super(application, dbExecutor, lifecycleManager, db, androidExecutor,
				identityManager, notificationManager, cryptoExecutor, clock,
				messageTracker, eventBus);
		this.forumManager = forumManager;
		this.forumSharingManager = forumSharingManager;
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ForumPostReceivedEvent) {
			ForumPostReceivedEvent f = (ForumPostReceivedEvent) e;
			if (f.getGroupId().equals(groupId)) {
				LOG.info("Forum post received, adding...");
				ForumPostItem item = buildItem(f.getHeader(), f.getText());
				addItem(item);
			}
		} else {
			super.eventOccurred(e);
		}
	}

	void clearForumPostNotification() {
		notificationManager.clearForumPostNotification(groupId);
	}

	LiveData<Forum> loadForum() {
		MutableLiveData<Forum> forum = new MutableLiveData<>();
		runOnDbThread(() -> {
			try {
				Forum f = forumManager.getForum(groupId);
				forum.postValue(f);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
		return forum;
	}

	@Override
	public void loadItems() {
		loadList(txn -> {
			long start = now();
			List<ForumPostHeader> headers =
					forumManager.getPostHeaders(txn, groupId);
			logDuration(LOG, "Loading headers", start);
			return createItems(txn, headers, this::buildItem);
		}, this::setItems);
	}

	@Override
	public void createAndStoreMessage(String text,
			@Nullable ForumPostItem parentItem) {
		runOnDbThread(() -> {
			try {
				LocalAuthor author = identityManager.getLocalAuthor();
				GroupCount count = forumManager.getGroupCount(groupId);
				long timestamp = max(count.getLatestMsgTime() + 1,
						clock.currentTimeMillis());
				MessageId parentId =
						parentItem != null ? parentItem.getId() : null;
				createMessage(text, timestamp, parentId, author);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	private void createMessage(String text, long timestamp,
			@Nullable MessageId parentId, LocalAuthor author) {
		cryptoExecutor.execute(() -> {
			LOG.info("Creating forum post...");
			ForumPost msg = forumManager.createLocalPost(groupId, text,
					timestamp, parentId, author);
			storePost(msg, text);
		});
	}

	private void storePost(ForumPost msg, String text) {
		runOnDbThread(() -> {
			try {
				long start = now();
				ForumPostHeader header = forumManager.addLocalPost(msg);
				addItemAsync(buildItem(header, text));
				logDuration(LOG, "Storing forum post", start);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	private ForumPostItem buildItem(ForumPostHeader header, String text) {
		return new ForumPostItem(header, text);
	}

	@Override
	protected String loadMessageText(Transaction txn, PostHeader header)
			throws DbException {
		return forumManager.getPostText(txn, header.getId());
	}

	void deleteForum() {
		runOnDbThread(() -> {
			try {
				Forum f = forumManager.getForum(groupId);
				forumManager.removeForum(f);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
		Toast.makeText(getApplication(), R.string.forum_left_toast,
				LENGTH_SHORT).show();
	}

}
