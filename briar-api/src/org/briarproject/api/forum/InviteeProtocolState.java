package org.briarproject.api.forum;

import static org.briarproject.api.forum.InviteeAction.LOCAL_ACCEPT;
import static org.briarproject.api.forum.InviteeAction.LOCAL_DECLINE;
import static org.briarproject.api.forum.InviteeAction.LOCAL_LEAVE;
import static org.briarproject.api.forum.InviteeAction.REMOTE_INVITATION;
import static org.briarproject.api.forum.InviteeAction.REMOTE_LEAVE;

public enum InviteeProtocolState {

	ERROR(0),
	AWAIT_INVITATION(1) {
		@Override
		public InviteeProtocolState next(InviteeAction a) {
			if (a == REMOTE_INVITATION) return AWAIT_LOCAL_RESPONSE;
			return ERROR;
		}
	},
	AWAIT_LOCAL_RESPONSE(2) {
		@Override
		public InviteeProtocolState next(InviteeAction a) {
			if (a == LOCAL_ACCEPT || a == LOCAL_DECLINE) return FINISHED;
			if (a == REMOTE_LEAVE) return LEFT;
			return ERROR;
		}
	},
	FINISHED(3) {
		@Override
		public InviteeProtocolState next(InviteeAction a) {
			if (a == LOCAL_LEAVE || a == REMOTE_LEAVE) return LEFT;
			return FINISHED;
		}
	},
	LEFT(4) {
		@Override
		public InviteeProtocolState next(InviteeAction a) {
			if (a == LOCAL_LEAVE) return ERROR;
			return LEFT;
		}
	};

	private final int value;

	InviteeProtocolState(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}

	public static InviteeProtocolState fromValue(int value) {
		for (InviteeProtocolState s : values()) {
			if (s.value == value) return s;
		}
		throw new IllegalArgumentException();
	}

	public InviteeProtocolState next(InviteeAction a) {
		return this;
	}
}
