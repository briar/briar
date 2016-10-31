package org.briarproject.android.sharing;

import org.briarproject.android.controller.ActivityLifecycleController;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sharing.InvitationItem;

import java.util.Collection;

public interface InvitationController<I extends InvitationItem>
		extends ActivityLifecycleController {

	void loadInvitations(boolean clear,
			ResultExceptionHandler<Collection<I>, DbException> handler);

	void respondToInvitation(I item, boolean accept,
			ResultExceptionHandler<Void, DbException> handler);

	interface InvitationListener {

		void loadInvitations(boolean clear);

	}

}
