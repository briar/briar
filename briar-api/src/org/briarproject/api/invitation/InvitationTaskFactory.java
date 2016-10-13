package org.briarproject.api.invitation;

/** Creates tasks for exchanging invitations with remote peers. */
public interface InvitationTaskFactory {

	/** Creates a task using the local author and invitation codes. */
	InvitationTask createTask(int localCode, int remoteCode);
}
