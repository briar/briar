package org.briarproject.bramble.transport.agreement;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.transport.KeySetId;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.transport.agreement.TransportKeyAgreementConstants.SESSION_KEY_KEY_SET_ID;
import static org.briarproject.bramble.transport.agreement.TransportKeyAgreementConstants.SESSION_KEY_LAST_LOCAL_MESSAGE_ID;
import static org.briarproject.bramble.transport.agreement.TransportKeyAgreementConstants.SESSION_KEY_LOCAL_PRIVATE_KEY;
import static org.briarproject.bramble.transport.agreement.TransportKeyAgreementConstants.SESSION_KEY_LOCAL_PUBLIC_KEY;
import static org.briarproject.bramble.transport.agreement.TransportKeyAgreementConstants.SESSION_KEY_LOCAL_TIMESTAMP;
import static org.briarproject.bramble.transport.agreement.TransportKeyAgreementConstants.SESSION_KEY_STATE;

@Immutable
@NotNullByDefault
class SessionParserImpl implements SessionParser {

	private final TransportKeyAgreementCrypto crypto;

	@Inject
	SessionParserImpl(TransportKeyAgreementCrypto crypto) {
		this.crypto = crypto;
	}

	@Override
	public Session parseSession(BdfDictionary meta) throws FormatException {
		State state = State.fromValue(meta.getInt(SESSION_KEY_STATE));

		MessageId lastLocalMessageId = null;
		byte[] lastLocalMessageIdBytes =
				meta.getOptionalRaw(SESSION_KEY_LAST_LOCAL_MESSAGE_ID);
		if (lastLocalMessageIdBytes != null) {
			lastLocalMessageId = new MessageId(lastLocalMessageIdBytes);
		}

		KeyPair localKeyPair = null;
		byte[] localPublicKeyBytes =
				meta.getOptionalRaw(SESSION_KEY_LOCAL_PUBLIC_KEY);
		byte[] localPrivateKeyBytes =
				meta.getOptionalRaw(SESSION_KEY_LOCAL_PRIVATE_KEY);
		if (localPublicKeyBytes != null && localPrivateKeyBytes != null) {
			PublicKey pub = crypto.parsePublicKey(localPublicKeyBytes);
			PrivateKey priv = crypto.parsePrivateKey(localPrivateKeyBytes);
			localKeyPair = new KeyPair(pub, priv);
		}

		Long localTimestamp = meta.getOptionalLong(SESSION_KEY_LOCAL_TIMESTAMP);

		KeySetId keySetId = null;
		Integer keySetIdInt = meta.getOptionalInt(SESSION_KEY_KEY_SET_ID);
		if (keySetIdInt != null) {
			keySetId = new KeySetId(keySetIdInt);
		}

		return new Session(state, lastLocalMessageId, localKeyPair,
				localTimestamp, keySetId);
	}
}
