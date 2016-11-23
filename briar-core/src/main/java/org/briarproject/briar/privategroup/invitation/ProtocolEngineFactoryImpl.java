package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.privategroup.GroupMessageFactory;
import org.briarproject.briar.api.privategroup.PrivateGroupFactory;
import org.briarproject.briar.api.privategroup.PrivateGroupManager;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
class ProtocolEngineFactoryImpl implements ProtocolEngineFactory {

	private final DatabaseComponent db;
	private final ClientHelper clientHelper;
	private final PrivateGroupManager privateGroupManager;
	private final PrivateGroupFactory privateGroupFactory;
	private final GroupMessageFactory groupMessageFactory;
	private final IdentityManager identityManager;
	private final MessageParser messageParser;
	private final MessageEncoder messageEncoder;
	private final MessageTracker messageTracker;
	private final Clock clock;

	@Inject
	ProtocolEngineFactoryImpl(DatabaseComponent db, ClientHelper clientHelper,
			PrivateGroupManager privateGroupManager,
			PrivateGroupFactory privateGroupFactory,
			GroupMessageFactory groupMessageFactory,
			IdentityManager identityManager, MessageParser messageParser,
			MessageEncoder messageEncoder, MessageTracker messageTracker,
			Clock clock) {
		this.db = db;
		this.clientHelper = clientHelper;
		this.privateGroupManager = privateGroupManager;
		this.privateGroupFactory = privateGroupFactory;
		this.groupMessageFactory = groupMessageFactory;
		this.identityManager = identityManager;
		this.messageParser = messageParser;
		this.messageEncoder = messageEncoder;
		this.messageTracker = messageTracker;
		this.clock = clock;
	}

	@Override
	public ProtocolEngine<CreatorSession> createCreatorEngine() {
		return new CreatorProtocolEngine(db, clientHelper, privateGroupManager,
				privateGroupFactory, groupMessageFactory, identityManager,
				messageParser, messageEncoder, messageTracker, clock);
	}

	@Override
	public ProtocolEngine<InviteeSession> createInviteeEngine() {
		return new InviteeProtocolEngine(db, clientHelper, privateGroupManager,
				privateGroupFactory, groupMessageFactory, identityManager,
				messageParser, messageEncoder, messageTracker, clock);
	}

	@Override
	public ProtocolEngine<PeerSession> createPeerEngine() {
		return new PeerProtocolEngine(db, clientHelper, privateGroupManager,
				privateGroupFactory, groupMessageFactory, identityManager,
				messageParser, messageEncoder, messageTracker, clock);
	}
}
