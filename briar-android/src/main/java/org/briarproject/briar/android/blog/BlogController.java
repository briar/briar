package org.briarproject.briar.android.blog;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;

import java.util.Collection;

@NotNullByDefault
public interface BlogController  {

	void setGroupId(GroupId g);

	void loadSharingContacts(
			ResultExceptionHandler<Collection<ContactId>, DbException> handler);

}
