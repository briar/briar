package org.briarproject.android.threaded;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;

import org.briarproject.android.DestroyableContext;
import org.briarproject.android.controller.ActivityLifecycleController;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.clients.NamedGroup;
import org.briarproject.api.clients.PostHeader;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;

public interface ThreadListController<G extends NamedGroup, I extends ThreadItem, H extends PostHeader>
		extends ActivityLifecycleController {

	void setGroupId(GroupId groupId);

	void loadNamedGroup(ResultExceptionHandler<G, DbException> handler);

	void loadItem(H header, ResultExceptionHandler<I, DbException> handler);

	void loadItems(ResultExceptionHandler<Collection<I>, DbException> handler);

	void markItemRead(I item);

	void markItemsRead(Collection<I> items);

	void createAndStoreMessage(String body, @Nullable I parentItem,
			ResultExceptionHandler<I, DbException> handler);

	void deleteNamedGroup(ResultExceptionHandler<Void, DbException> handler);

	interface ThreadListListener<H> extends DestroyableContext {
		@UiThread
		void onHeaderReceived(H header);

		@UiThread
		void onGroupRemoved();

		Context getApplicationContext();
	}

}
