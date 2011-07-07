package net.sf.briar.invitation;

import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.invitation.InvitationCallback;
import net.sf.briar.api.invitation.InvitationParameters;
import net.sf.briar.api.invitation.InvitationWorkerFactory;

import com.google.inject.Inject;

class InvitationWorkerFactoryImpl implements InvitationWorkerFactory {

	private final DatabaseComponent databaseComponent;

	@Inject
	InvitationWorkerFactoryImpl(DatabaseComponent databaseComponent) {
		this.databaseComponent = databaseComponent;
	}

	public Runnable createWorker(InvitationCallback callback,
			InvitationParameters parameters) {
		return new InvitationWorker(callback, parameters, databaseComponent);
	}
}
