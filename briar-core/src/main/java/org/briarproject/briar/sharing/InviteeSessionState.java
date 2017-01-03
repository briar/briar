package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.briar.api.sharing.SharingConstants.INVITATION_ID;
import static org.briarproject.briar.api.sharing.SharingConstants.IS_SHARER;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_ABORT;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_INVITATION;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_LEAVE;
import static org.briarproject.briar.api.sharing.SharingConstants.STATE;
import static org.briarproject.briar.sharing.InviteeSessionState.Action.LOCAL_ACCEPT;
import static org.briarproject.briar.sharing.InviteeSessionState.Action.LOCAL_DECLINE;
import static org.briarproject.briar.sharing.InviteeSessionState.Action.LOCAL_LEAVE;
import static org.briarproject.briar.sharing.InviteeSessionState.Action.REMOTE_INVITATION;
import static org.briarproject.briar.sharing.InviteeSessionState.Action.REMOTE_LEAVE;

@Deprecated
@NotThreadSafe
@NotNullByDefault
public abstract class InviteeSessionState extends SharingSessionState {

	private State state;
	private final MessageId invitationId;

	public InviteeSessionState(SessionId sessionId, MessageId storageId,
			GroupId groupId, State state, ContactId contactId,
			GroupId shareableId, MessageId invitationId) {

		super(sessionId, storageId, groupId, contactId, shareableId);
		this.state = state;
		this.invitationId = invitationId;
	}

	@Override
	public BdfDictionary toBdfDictionary() {
		BdfDictionary d = super.toBdfDictionary();
		d.put(STATE, getState().getValue());
		d.put(IS_SHARER, false);
		d.put(INVITATION_ID, invitationId);
		return d;
	}

	public void setState(State state) {
		this.state = state;
	}

	public State getState() {
		return state;
	}

	public MessageId getInvitationId() {
		return invitationId;
	}

	@Immutable
	@NotNullByDefault
	public enum State {

		ERROR(0),
		AWAIT_INVITATION(1) {
			@Override
			public State next(Action a) {
				if (a == REMOTE_INVITATION) return AWAIT_LOCAL_RESPONSE;
				return ERROR;
			}
		},
		AWAIT_LOCAL_RESPONSE(2) {
			@Override
			public State next(Action a) {
				if (a == LOCAL_ACCEPT || a == LOCAL_DECLINE) return FINISHED;
				if (a == REMOTE_LEAVE) return LEFT;
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

	@NotNullByDefault
	public enum Action {

		LOCAL_ACCEPT,
		LOCAL_DECLINE,
		LOCAL_LEAVE,
		LOCAL_ABORT,
		REMOTE_INVITATION,
		REMOTE_LEAVE,
		REMOTE_ABORT;

		@Nullable
		public static Action getRemote(long type) {
			if (type == SHARE_MSG_TYPE_INVITATION) return REMOTE_INVITATION;
			if (type == SHARE_MSG_TYPE_LEAVE) return REMOTE_LEAVE;
			if (type == SHARE_MSG_TYPE_ABORT) return REMOTE_ABORT;
			return null;
		}
	}

}