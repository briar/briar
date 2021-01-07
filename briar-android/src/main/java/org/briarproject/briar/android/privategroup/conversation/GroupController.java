package org.briarproject.briar.android.privategroup.conversation;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;
import org.briarproject.briar.android.threaded.ThreadListController;
import org.briarproject.briar.api.privategroup.Visibility;

import androidx.annotation.UiThread;

public interface GroupController
		extends ThreadListController<GroupMessageItem> {

	void isDissolved(
			ResultExceptionHandler<Boolean, DbException> handler);

	interface GroupListener extends ThreadListListener<GroupMessageItem> {

		@UiThread
		void onContactRelationshipRevealed(AuthorId memberId,
				ContactId contactId, Visibility v);

		@UiThread
		void onGroupDissolved();
	}

}
