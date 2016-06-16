package org.briarproject.sharing;

import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import static org.briarproject.api.sharing.SharingConstants.IS_SHARER;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_ABORT;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_ACCEPT;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_DECLINE;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_LEAVE;
import static org.briarproject.api.sharing.SharingConstants.STATE;
import static org.briarproject.sharing.SharerSessionState.Action.LOCAL_INVITATION;
import static org.briarproject.sharing.SharerSessionState.Action.LOCAL_LEAVE;
import static org.briarproject.sharing.SharerSessionState.Action.REMOTE_ACCEPT;
import static org.briarproject.sharing.SharerSessionState.Action.REMOTE_DECLINE;
import static org.briarproject.sharing.SharerSessionState.Action.REMOTE_LEAVE;

// This class is not thread-safe
public abstract class SharerSessionState extends SharingSessionState {

	private State state;
	private String msg = null;

	public SharerSessionState(SessionId sessionId, MessageId storageId,
			GroupId groupId, State state, ContactId contactId,
			GroupId shareableId) {

		super(sessionId, storageId, groupId, contactId, shareableId);
		this.state = state;
	}

	public BdfDictionary toBdfDictionary() {
		BdfDictionary d = super.toBdfDictionary();
		d.put(STATE, getState().getValue());
		d.put(IS_SHARER, true);
		return d;
	}

	public void setState(State state) {
		this.state = state;
	}

	public State getState() {
		return state;
	}

	public void setMessage(String msg) {
		this.msg = msg;
	}

	public String getMessage() {
		return this.msg;
	}

	public enum State {
		ERROR(0),
		PREPARE_INVITATION(1) {
			@Override
			public State next(Action a) {
				if (a == LOCAL_INVITATION) return AWAIT_RESPONSE;
				return ERROR;
			}
		},
		AWAIT_RESPONSE(2) {
			@Override
			public State next(Action a) {
				if (a == REMOTE_ACCEPT || a == REMOTE_DECLINE) return FINISHED;
				if (a == LOCAL_LEAVE) return LEFT;
				return ERROR;
			}
		},
		FINISHED(3) {
			@Override
			public State next(Action a) {
				if (a == LOCAL_LEAVE || a == REMOTE_LEAVE) return LEFT;
				return FINISHED;
			}
		},
		LEFT(4) {
			@Override
			public State next(Action a) {
				if (a == LOCAL_LEAVE) return ERROR;
				return LEFT;
			}
		};

		private final int value;

		State(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}

		public static State fromValue(int value) {
			for (State s : values()) {
				if (s.value == value) return s;
			}
			throw new IllegalArgumentException();
		}

		public State next(Action a) {
			return this;
		}
	}

	public enum Action {
		LOCAL_INVITATION,
		LOCAL_LEAVE,
		LOCAL_ABORT,
		REMOTE_ACCEPT,
		REMOTE_DECLINE,
		REMOTE_LEAVE,
		REMOTE_ABORT;

		public static Action getRemote(long type) {
			if (type == SHARE_MSG_TYPE_ACCEPT) return REMOTE_ACCEPT;
			if (type == SHARE_MSG_TYPE_DECLINE) return REMOTE_DECLINE;
			if (type == SHARE_MSG_TYPE_LEAVE) return REMOTE_LEAVE;
			if (type == SHARE_MSG_TYPE_ABORT) return REMOTE_ABORT;
			return null;
		}
	}

}