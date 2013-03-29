package net.sf.briar.api.invitation;

import net.sf.briar.api.AuthorId;

/** Creates tasks for exchanging invitations with remote peers. */
public interface InvitationTaskFactory {

	/** Creates a task using the given pseudonym and invitation codes. */
	InvitationTask createTask(AuthorId localAuthorId, int localCode,
			int remoteCode);
}
