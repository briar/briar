package org.briarproject.introduction;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;

import static org.briarproject.api.introduction.IntroductionConstants.CONTACT;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE_INTRODUCEE;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE_INTRODUCER;
import static org.briarproject.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.api.introduction.IntroductionConstants.STORAGE_ID;

// This class is not thread-safe
abstract class IntroductionState {

	private SessionId sessionId;
	private final MessageId storageId;

	IntroductionState(SessionId sessionId, MessageId storageId) {
		this.sessionId = sessionId;
		this.storageId = storageId;
	}

	public BdfDictionary toBdfDictionary() {
		BdfDictionary d = new BdfDictionary();
		d.put(SESSION_ID, sessionId);
		d.put(STORAGE_ID, getStorageId());
		return d;
	}

	static IntroductionState fromBdfDictionary(BdfDictionary state)
		throws FormatException {

		int role = state.getLong(ROLE).intValue();
		if (role == ROLE_INTRODUCER) {
			return IntroducerSessionState.fromBdfDictionary(state);
		} else if(role == ROLE_INTRODUCEE) {
			return IntroduceeSessionState.fromBdfDictionary(state);
		} else {
			throw new FormatException();
		}
	}

	public SessionId getSessionId() {
		return sessionId;
	}

	public MessageId getStorageId() {
		return storageId;
	}

	public void setSessionId(SessionId sessionId) {
		this.sessionId = sessionId;
	}

}


