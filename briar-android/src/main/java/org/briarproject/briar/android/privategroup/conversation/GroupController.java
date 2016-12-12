package org.briarproject.briar.android.privategroup.conversation;

import android.support.annotation.UiThread;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;
import org.briarproject.briar.android.threaded.ThreadListController;
import org.briarproject.briar.api.privategroup.GroupMessageHeader;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.Visibility;

public interface GroupController
		extends
		ThreadListController<PrivateGroup, GroupMessageItem, GroupMessageHeader> {

	void loadLocalAuthor(
			ResultExceptionHandler<LocalAuthor, DbException> handler);

	void isDissolved(
			ResultExceptionHandler<Boolean, DbException> handler);

	interface GroupListener extends ThreadListListener<GroupMessageHeader> {
		@UiThread
		void onContactRelationshipRevealed(AuthorId memberId,
				ContactId contactId, Visibility v);

		@UiThread
		void onGroupDissolved();
	}

}
