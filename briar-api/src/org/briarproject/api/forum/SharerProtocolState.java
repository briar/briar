package org.briarproject.api.forum;

import static org.briarproject.api.forum.SharerAction.LOCAL_INVITATION;
import static org.briarproject.api.forum.SharerAction.LOCAL_LEAVE;
import static org.briarproject.api.forum.SharerAction.REMOTE_ACCEPT;
import static org.briarproject.api.forum.SharerAction.REMOTE_DECLINE;
import static org.briarproject.api.forum.SharerAction.REMOTE_LEAVE;

public enum SharerProtocolState {

	ERROR(0),
	PREPARE_INVITATION(1) {
		@Override
		public SharerProtocolState next(SharerAction a) {
			if (a == LOCAL_INVITATION) return AWAIT_RESPONSE;
			return ERROR;
		}
	},
	AWAIT_RESPONSE(2) {
		@Override
		public SharerProtocolState next(SharerAction a) {
			if (a == REMOTE_ACCEPT || a == REMOTE_DECLINE) return FINISHED;
			if (a == LOCAL_LEAVE) return LEFT;
			return ERROR;
		}
	},
	FINISHED(3) {
		@Override
		public SharerProtocolState next(SharerAction a) {
			if (a == LOCAL_LEAVE || a == REMOTE_LEAVE) return LEFT;
			return FINISHED;
		}
	},
	LEFT(4) {
		@Override
		public SharerProtocolState next(SharerAction a) {
			if (a == LOCAL_LEAVE) return ERROR;
			return LEFT;
		}
	};

	private final int value;

	SharerProtocolState(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}

	public static SharerProtocolState fromValue(int value) {
		for (SharerProtocolState s : values()) {
			if (s.value == value) return s;
		}
		throw new IllegalArgumentException();
	}

	public SharerProtocolState next(SharerAction a) {
		return this;
	}
}
