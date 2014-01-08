package org.briarproject.api.invitation;

import org.briarproject.api.AuthorId;

/** Creates tasks for exchanging invitations with remote peers. */
public interface InvitationTaskFactory {

	/** Creates a task using the given pseudonym and invitation codes. */
	InvitationTask createTask(AuthorId localAuthorId, int localCode,
			int remoteCode);
}
