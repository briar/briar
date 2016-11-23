package org.briarproject.bramble.api.invitation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

/**
 * Creates tasks for exchanging invitations with remote peers.
 */
@NotNullByDefault
public interface InvitationTaskFactory {

	/**
	 * Creates a task using the given local and remote invitation codes.
	 */
	InvitationTask createTask(int localCode, int remoteCode);
}
