package org.briarproject.briar.android.threaded;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.android.controller.ActivityLifecycleController;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;

import java.util.Collection;

import javax.annotation.Nullable;

import androidx.annotation.UiThread;

@NotNullByDefault
public interface ThreadListController<I extends ThreadItem>
		extends ActivityLifecycleController {

	void setGroupId(GroupId groupId);

	void loadSharingContacts(
			ResultExceptionHandler<Collection<ContactId>, DbException> handler);

	void markItemRead(I item);

	void markItemsRead(Collection<I> items);

	interface ThreadListListener<I> extends ThreadListDataSource {

		@UiThread
		void onItemReceived(I item);

		@UiThread
		void onGroupRemoved();

		@UiThread
		void onInvitationAccepted(ContactId c);
	}

	interface ThreadListDataSource {

		@UiThread
		@Nullable
		MessageId getFirstVisibleMessageId();
	}

}
