package org.briarproject.android.sharing;

import android.support.annotation.UiThread;

import org.briarproject.android.DestroyableContext;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;

public interface ContactSelectorListener extends DestroyableContext {

	@Deprecated
	void runOnDbThread(Runnable runnable);

	@DatabaseExecutor
	boolean isDisabled(GroupId groupId, Contact c) throws DbException;

	@UiThread
	void contactsSelected(GroupId groupId, Collection<ContactId> contacts);

	@UiThread
	void onBackPressed();

}
