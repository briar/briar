package net.sf.briar.api.plugins;

import net.sf.briar.api.plugins.duplex.DuplexPlugin;

public interface InvitationStarter {

	void startIncomingInvitation(DuplexPlugin plugin,
			IncomingInvitationCallback callback);

	void startOutgoingInvitation(DuplexPlugin plugin,
			OutgoingInvitationCallback callback);
}
