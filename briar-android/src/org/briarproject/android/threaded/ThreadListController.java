package org.briarproject.android.threaded;

import android.support.annotation.Nullable;
import android.support.annotation.UiThread;

import org.briarproject.android.DestroyableContext;
import org.briarproject.android.controller.ActivityLifecycleController;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.clients.BaseGroup;
import org.briarproject.api.clients.PostHeader;
import org.briarproject.api.db.DbException;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

public interface ThreadListController<G extends BaseGroup, I extends ThreadItem, H extends PostHeader>
		extends ActivityLifecycleController {

	void setGroupId(GroupId groupId);

	void loadGroupItem(ResultExceptionHandler<G, DbException> handler);

	void loadItem(H header, ResultExceptionHandler<I, DbException> handler);

	void loadItems(ResultExceptionHandler<Collection<I>, DbException> handler);

	void markItemRead(I item);

	void markItemsRead(Collection<I> items);

	void send(String body, ResultExceptionHandler<I, DbException> handler);

	void send(String body, @Nullable MessageId parentId,
			ResultExceptionHandler<I, DbException> handler);

	void deleteGroupItem(ResultExceptionHandler<Void, DbException> handler);

	interface ThreadListListener<H> extends DestroyableContext {
		@UiThread
		void onHeaderReceived(H header);

		@UiThread
		void onGroupRemoved();
	}

}
