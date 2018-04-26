package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.transport.KeySetId;
import org.briarproject.briar.introduction.IntroducerSession.Introducee;

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.api.data.BdfDictionary.NULL_VALUE;
import static org.briarproject.briar.api.introduction.Role.INTRODUCEE;
import static org.briarproject.briar.api.introduction.Role.INTRODUCER;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_ACCEPT_TIMESTAMP;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_AUTHOR;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_EPHEMERAL_PRIVATE_KEY;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_EPHEMERAL_PUBLIC_KEY;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_GROUP_ID;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_INTRODUCEE_A;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_INTRODUCEE_B;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_INTRODUCER;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_LAST_LOCAL_MESSAGE_ID;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_LAST_REMOTE_MESSAGE_ID;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_LOCAL_TIMESTAMP;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_MASTER_KEY;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_REMOTE_ACCEPT_TIMESTAMP;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_REMOTE_AUTHOR;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_REMOTE_EPHEMERAL_PUBLIC_KEY;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_REMOTE_TRANSPORT_PROPERTIES;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_REQUEST_TIMESTAMP;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_ROLE;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_SESSION_ID;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_STATE;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_TRANSPORT_KEYS;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_TRANSPORT_PROPERTIES;

@Immutable
@NotNullByDefault
class SessionEncoderImpl implements SessionEncoder {

	private final ClientHelper clientHelper;

	@Inject
	SessionEncoderImpl(ClientHelper clientHelper) {
		this.clientHelper = clientHelper;
	}

	@Override
	public BdfDictionary getIntroduceeSessionsByIntroducerQuery(
			Author introducer) {
		return BdfDictionary.of(
				new BdfEntry(SESSION_KEY_ROLE, INTRODUCEE.getValue()),
				new BdfEntry(SESSION_KEY_INTRODUCER,
						clientHelper.toList(introducer))
		);
	}

	@Override
	public BdfDictionary getIntroducerSessionsQuery() {
		return BdfDictionary.of(
				new BdfEntry(SESSION_KEY_ROLE, INTRODUCER.getValue())
		);
	}

	@Override
	public BdfDictionary encodeIntroducerSession(IntroducerSession s) {
		BdfDictionary d = encodeSession(s);
		d.put(SESSION_KEY_INTRODUCEE_A, encodeIntroducee(s.getIntroduceeA()));
		d.put(SESSION_KEY_INTRODUCEE_B, encodeIntroducee(s.getIntroduceeB()));
		return d;
	}

	private BdfDictionary encodeIntroducee(Introducee i) {
		BdfDictionary d = new BdfDictionary();
		putNullable(d, SESSION_KEY_LAST_LOCAL_MESSAGE_ID, i.lastLocalMessageId);
		putNullable(d, SESSION_KEY_LAST_REMOTE_MESSAGE_ID,
				i.lastRemoteMessageId);
		d.put(SESSION_KEY_LOCAL_TIMESTAMP, i.localTimestamp);
		d.put(SESSION_KEY_GROUP_ID, i.groupId);
		d.put(SESSION_KEY_AUTHOR, clientHelper.toList(i.author));
		return d;
	}

	@Override
	public BdfDictionary encodeIntroduceeSession(IntroduceeSession s) {
		BdfDictionary d = encodeSession(s);
		d.put(SESSION_KEY_LOCAL_TIMESTAMP, s.getLocalTimestamp());
		putNullable(d, SESSION_KEY_LAST_LOCAL_MESSAGE_ID,
				s.getLastLocalMessageId());
		putNullable(d, SESSION_KEY_LAST_REMOTE_MESSAGE_ID,
				s.getLastRemoteMessageId());
		d.put(SESSION_KEY_INTRODUCER, clientHelper.toList(s.getIntroducer()));
		d.put(SESSION_KEY_REMOTE_AUTHOR,
				clientHelper.toList(s.getRemoteAuthor()));
		putNullable(d, SESSION_KEY_EPHEMERAL_PUBLIC_KEY,
				s.getEphemeralPublicKey());
		putNullable(d, SESSION_KEY_EPHEMERAL_PRIVATE_KEY,
				s.getEphemeralPrivateKey());
		putNullable(d, SESSION_KEY_TRANSPORT_PROPERTIES,
				s.getTransportProperties() == null ? null :
						clientHelper.toDictionary(s.getTransportProperties()));
		d.put(SESSION_KEY_ACCEPT_TIMESTAMP, s.getAcceptTimestamp());
		putNullable(d, SESSION_KEY_MASTER_KEY, s.getMasterKey());
		putNullable(d, SESSION_KEY_REMOTE_EPHEMERAL_PUBLIC_KEY,
				s.getRemotePublicKey());
		putNullable(d, SESSION_KEY_REMOTE_TRANSPORT_PROPERTIES,
				s.getRemoteTransportProperties() == null ? null : clientHelper
						.toDictionary(s.getRemoteTransportProperties()));
		d.put(SESSION_KEY_REMOTE_ACCEPT_TIMESTAMP, s.getRemoteAcceptTimestamp());
		putNullable(d, SESSION_KEY_TRANSPORT_KEYS,
				encodeTransportKeys(s.getTransportKeys()));
		return d;
	}

	private BdfDictionary encodeSession(Session s) {
		BdfDictionary d = new BdfDictionary();
		d.put(SESSION_KEY_SESSION_ID, s.getSessionId());
		d.put(SESSION_KEY_ROLE, s.getRole().getValue());
		d.put(SESSION_KEY_STATE, s.getState().getValue());
		d.put(SESSION_KEY_REQUEST_TIMESTAMP, s.getRequestTimestamp());
		return d;
	}

	@Nullable
	private BdfDictionary encodeTransportKeys(
			@Nullable Map<TransportId, KeySetId> keys) {
		if (keys == null) return null;
		BdfDictionary d = new BdfDictionary();
		for (Map.Entry<TransportId, KeySetId> e : keys.entrySet()) {
			d.put(e.getKey().getString(), e.getValue().getInt());
		}
		return d;
	}

	private void putNullable(BdfDictionary d, String key, @Nullable Object o) {
		d.put(key, o == null ? NULL_VALUE : o);
	}

}
