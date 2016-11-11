package org.briarproject.android.sharing;

import org.briarproject.android.controller.ActivityLifecycleController;
import org.briarproject.android.controller.handler.ExceptionHandler;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.db.DbException;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sharing.InvitationItem;

import java.util.Collection;

@NotNullByDefault
public interface InvitationController<I extends InvitationItem>
		extends ActivityLifecycleController {

	void loadInvitations(boolean clear,
			ResultExceptionHandler<Collection<I>, DbException> handler);

	void respondToInvitation(I item, boolean accept,
			ExceptionHandler<DbException> handler);

	interface InvitationListener {

		void loadInvitations(boolean clear);

	}

}
