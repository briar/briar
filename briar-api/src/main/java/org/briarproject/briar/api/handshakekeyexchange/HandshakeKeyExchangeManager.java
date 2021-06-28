package org.briarproject.briar.api.handshakekeyexchange;

import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.briar.api.conversation.ConversationManager;

public interface HandshakeKeyExchangeManager extends ConversationManager.ConversationClient {

	/**
	 * The unique ID of the client.
	 */
	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.handshakekeyexchange");

	/**
	 * The current major version of the handshake key exchange client.
	 */
	int MAJOR_VERSION = 0;

	/**
	 * The current minor version of the handshake key exchange client.
	 */
	int MINOR_VERSION = 0;
}
