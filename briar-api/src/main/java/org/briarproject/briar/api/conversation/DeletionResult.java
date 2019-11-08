package org.briarproject.briar.api.conversation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.briar.api.conversation.ConversationManager.DELETE_NOT_DOWNLOADED;
import static org.briarproject.briar.api.conversation.ConversationManager.DELETE_SESSION_INCOMPLETE;
import static org.briarproject.briar.api.conversation.ConversationManager.DELETE_SESSION_IN_PROGRESS;
import static org.briarproject.briar.api.conversation.ConversationManager.DELETE_SESSION_IS_INTRODUCTION;
import static org.briarproject.briar.api.conversation.ConversationManager.DELETE_SESSION_IS_INVITATION;

@NotThreadSafe
@NotNullByDefault
public class DeletionResult {

	private int result = 0;

	public void addDeletionResult(DeletionResult deletionResult) {
		result |= deletionResult.result;
	}

	public void addInvitationNotAllSelected() {
		result |= DELETE_SESSION_INCOMPLETE | DELETE_SESSION_IS_INVITATION;
	}

	public void addInvitationSessionInProgress() {
		result |= DELETE_SESSION_IN_PROGRESS | DELETE_SESSION_IS_INVITATION;
	}

	public void addIntroductionNotAllSelected() {
		result |= DELETE_SESSION_INCOMPLETE | DELETE_SESSION_IS_INTRODUCTION;
	}

	public void addIntroductionSessionInProgress() {
		result |= DELETE_SESSION_IN_PROGRESS | DELETE_SESSION_IS_INTRODUCTION;
	}

	public void addNotFullyDownloaded() {
		result |= DELETE_NOT_DOWNLOADED;
	}

	public boolean allDeleted() {
		return result == 0;
	}

	public boolean hasIntroduction() {
		return (result & DELETE_SESSION_IS_INTRODUCTION) != 0;
	}

	public boolean hasInvitation() {
		return (result & DELETE_SESSION_IS_INVITATION) != 0;
	}

	public boolean hasSessionInProgress() {
		return (result & DELETE_SESSION_IN_PROGRESS) != 0;
	}

	public boolean hasNotAllSelected() {
		return (result & DELETE_SESSION_INCOMPLETE) != 0;
	}

	public boolean hasNotFullyDownloaded() {
		return (result & DELETE_NOT_DOWNLOADED) != 0;
	}

}
