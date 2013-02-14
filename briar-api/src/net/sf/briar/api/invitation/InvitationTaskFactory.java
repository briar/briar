package net.sf.briar.api.invitation;

/** Creates tasks for exchanging invitations with remote peers. */
public interface InvitationTaskFactory {

	/** Creates a task using the given invitation codes. */
	InvitationTask createTask(int localCode, int remoteCode);
}
