package org.briarproject.android.privategroup.reveal;

import org.briarproject.android.contactselection.ContactSelectorController;
import org.briarproject.android.controller.handler.ExceptionHandler;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;

@NotNullByDefault
public interface RevealContactsController
		extends ContactSelectorController<RevealableContactItem> {

	void isOnboardingNeeded(
			ResultExceptionHandler<Boolean, DbException> handler);

	void onboardingShown(ExceptionHandler<DbException> handler);

	void reveal(GroupId g, Collection<ContactId> contacts,
			ExceptionHandler<DbException> handler);

}
