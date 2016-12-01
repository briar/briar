package org.briarproject.briar.api.introduction;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.briar.api.introduction.IntroduceeAction.ACK;
import static org.briarproject.briar.api.introduction.IntroduceeAction.LOCAL_ACCEPT;
import static org.briarproject.briar.api.introduction.IntroduceeAction.LOCAL_DECLINE;
import static org.briarproject.briar.api.introduction.IntroduceeAction.REMOTE_ACCEPT;
import static org.briarproject.briar.api.introduction.IntroduceeAction.REMOTE_DECLINE;
import static org.briarproject.briar.api.introduction.IntroduceeAction.REMOTE_REQUEST;

@Immutable
@NotNullByDefault
public enum IntroduceeProtocolState {

	ERROR(0),
	AWAIT_REQUEST(1) {
		@Override
		public IntroduceeProtocolState next(IntroduceeAction a) {
			if (a == REMOTE_REQUEST) return AWAIT_RESPONSES;
			return ERROR;
		}
	},
	AWAIT_RESPONSES(2) {
		@Override
		public IntroduceeProtocolState next(IntroduceeAction a) {
			if (a == REMOTE_ACCEPT) return AWAIT_LOCAL_RESPONSE;
			if (a == REMOTE_DECLINE) return FINISHED;
			if (a == LOCAL_ACCEPT) return AWAIT_REMOTE_RESPONSE;
			if (a == LOCAL_DECLINE) return FINISHED;
			return ERROR;
		}
	},
	AWAIT_REMOTE_RESPONSE(3) {
		@Override
		public IntroduceeProtocolState next(IntroduceeAction a) {
			if (a == REMOTE_ACCEPT) return AWAIT_ACK;
			if (a == REMOTE_DECLINE) return FINISHED;
			return ERROR;
		}
	},
	AWAIT_LOCAL_RESPONSE(4) {
		@Override
		public IntroduceeProtocolState next(IntroduceeAction a) {
			if (a == LOCAL_ACCEPT) return AWAIT_ACK;
			if (a == LOCAL_DECLINE) return FINISHED;
			return ERROR;
		}
	},
	AWAIT_ACK(5) {
		@Override
		public IntroduceeProtocolState next(IntroduceeAction a) {
			if (a == ACK) return FINISHED;
			return ERROR;
		}
	},
	FINISHED(6);

	private final int value;

	IntroduceeProtocolState(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}

	public static IntroduceeProtocolState fromValue(int value) {
		for (IntroduceeProtocolState s : values()) {
			if (s.value == value) return s;
		}
		throw new IllegalArgumentException();
	}

	public IntroduceeProtocolState next(IntroduceeAction a) {
		return this;
	}

}
