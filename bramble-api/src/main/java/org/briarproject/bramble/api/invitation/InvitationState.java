package org.briarproject.bramble.api.invitation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * A snapshot of the state of an {@link InvitationTask}.
 */
@Immutable
@NotNullByDefault
public class InvitationState {

	private final int localInvitationCode, remoteInvitationCode;
	private final int localConfirmationCode, remoteConfirmationCode;
	private final boolean connected, connectionFailed;
	private final boolean localCompared, remoteCompared;
	private final boolean localMatched, remoteMatched;
	@Nullable
	private final String contactName;

	public InvitationState(int localInvitationCode, int remoteInvitationCode,
			int localConfirmationCode, int remoteConfirmationCode,
			boolean connected, boolean connectionFailed, boolean localCompared,
			boolean remoteCompared, boolean localMatched,
			boolean remoteMatched, @Nullable String contactName) {
		this.localInvitationCode = localInvitationCode;
		this.remoteInvitationCode = remoteInvitationCode;
		this.localConfirmationCode = localConfirmationCode;
		this.remoteConfirmationCode = remoteConfirmationCode;
		this.connected = connected;
		this.connectionFailed = connectionFailed;
		this.localCompared = localCompared;
		this.remoteCompared = remoteCompared;
		this.localMatched = localMatched;
		this.remoteMatched = remoteMatched;
		this.contactName = contactName;
	}

	public int getLocalInvitationCode() {
		return localInvitationCode;
	}

	public int getRemoteInvitationCode() {
		return remoteInvitationCode;
	}

	public int getLocalConfirmationCode() {
		return localConfirmationCode;
	}

	public int getRemoteConfirmationCode() {
		return remoteConfirmationCode;
	}

	public boolean getConnected() {
		return connected;
	}

	public boolean getConnectionFailed() {
		return connectionFailed;
	}

	public boolean getLocalCompared() {
		return localCompared;
	}

	public boolean getRemoteCompared() {
		return remoteCompared;
	}

	public boolean getLocalMatched() {
		return localMatched;
	}

	public boolean getRemoteMatched() {
		return remoteMatched;
	}

	@Nullable
	public String getContactName() {
		return contactName;
	}
}
