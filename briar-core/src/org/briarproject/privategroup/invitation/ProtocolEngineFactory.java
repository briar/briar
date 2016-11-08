package org.briarproject.privategroup.invitation;

import org.briarproject.api.nullsafety.NotNullByDefault;

@NotNullByDefault
interface ProtocolEngineFactory {

	ProtocolEngine<CreatorSession> createCreatorEngine();

	ProtocolEngine<InviteeSession> createInviteeEngine();

	ProtocolEngine<PeerSession> createPeerEngine();
}
