package org.briarproject.briar.android.privategroup.conversation;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.briar.android.threaded.ThreadListController;
import org.briarproject.briar.api.privategroup.Visibility;

import androidx.annotation.UiThread;

public interface GroupController
		extends ThreadListController<GroupMessageItem> {

	interface GroupListener extends ThreadListListener<GroupMessageItem> {
		@UiThread
		void onContactRelationshipRevealed(AuthorId memberId,
				ContactId contactId, Visibility v);
	}

}
