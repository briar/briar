package net.sf.briar.invitation;

import net.sf.briar.api.invitation.InvitationCallback;
import net.sf.briar.api.invitation.InvitationParameters;
import net.sf.briar.api.invitation.InvitationWorkerFactory;

class InvitationWorkerFactoryImpl implements InvitationWorkerFactory {

	public Runnable createWorker(InvitationCallback callback,
			InvitationParameters parameters) {
		return new InvitationWorker(callback, parameters);
	}
}
