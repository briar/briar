package net.sf.briar.api.invitation;

public interface InvitationWorkerFactory {

	Runnable createWorker(InvitationCallback callback,
			InvitationParameters parameters);
}
