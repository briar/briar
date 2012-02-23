package net.sf.briar.api.plugins;

public interface OutgoingInvitationCallback extends InvitationCallback {

	void showInvitationCode(int code);
}
