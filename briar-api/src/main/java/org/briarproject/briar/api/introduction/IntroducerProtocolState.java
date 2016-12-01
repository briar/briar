package org.briarproject.briar.api.introduction;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.briar.api.introduction.IntroducerAction.ACK_1;
import static org.briarproject.briar.api.introduction.IntroducerAction.ACK_2;
import static org.briarproject.briar.api.introduction.IntroducerAction.LOCAL_REQUEST;
import static org.briarproject.briar.api.introduction.IntroducerAction.REMOTE_ABORT;
import static org.briarproject.briar.api.introduction.IntroducerAction.REMOTE_ACCEPT_1;
import static org.briarproject.briar.api.introduction.IntroducerAction.REMOTE_ACCEPT_2;
import static org.briarproject.briar.api.introduction.IntroducerAction.REMOTE_DECLINE_1;
import static org.briarproject.briar.api.introduction.IntroducerAction.REMOTE_DECLINE_2;

@Immutable
@NotNullByDefault
public enum IntroducerProtocolState {

	ERROR(0),
	PREPARE_REQUESTS(1) {
		@Override
		public IntroducerProtocolState next(IntroducerAction a) {
			if (a == LOCAL_REQUEST) return AWAIT_RESPONSES;
			return ERROR;
		}
	},
	AWAIT_RESPONSES(2) {
		@Override
		public IntroducerProtocolState next(IntroducerAction a) {
			if (a == REMOTE_ACCEPT_1) return AWAIT_RESPONSE_2;
			if (a == REMOTE_ACCEPT_2) return AWAIT_RESPONSE_1;
			if (a == REMOTE_DECLINE_1) return FINISHED;
			if (a == REMOTE_DECLINE_2) return FINISHED;
			return ERROR;
		}
	},
	AWAIT_RESPONSE_1(3) {
		@Override
		public IntroducerProtocolState next(IntroducerAction a) {
			if (a == REMOTE_ACCEPT_1) return AWAIT_ACKS;
			if (a == REMOTE_DECLINE_1) return FINISHED;
			return ERROR;
		}
	},
	AWAIT_RESPONSE_2(4) {
		@Override
		public IntroducerProtocolState next(IntroducerAction a) {
			if (a == REMOTE_ACCEPT_2) return AWAIT_ACKS;
			if (a == REMOTE_DECLINE_2) return FINISHED;
			return ERROR;
		}
	},
	AWAIT_ACKS(5) {
		@Override
		public IntroducerProtocolState next(IntroducerAction a) {
			if (a == ACK_1) return AWAIT_ACK_2;
			if (a == ACK_2) return AWAIT_ACK_1;
			return ERROR;
		}
	},
	AWAIT_ACK_1(6) {
		@Override
		public IntroducerProtocolState next(IntroducerAction a) {
			if (a == ACK_1) return FINISHED;
			return ERROR;
		}
	},
	AWAIT_ACK_2(7) {
		@Override
		public IntroducerProtocolState next(IntroducerAction a) {
			if (a == ACK_2) return FINISHED;
			return ERROR;
		}
	},
	FINISHED(8) {
		@Override
		public IntroducerProtocolState next(IntroducerAction a) {
			if (a == REMOTE_ABORT) return ERROR;
			return FINISHED;
		}
	};

	private final int value;

	IntroducerProtocolState(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}

	public static IntroducerProtocolState fromValue(int value) {
		for (IntroducerProtocolState s : values()) {
			if (s.value == value) return s;
		}
		throw new IllegalArgumentException();
	}

	public static boolean isOngoing(IntroducerProtocolState state) {
		return state != FINISHED && state != ERROR;
	}

	public IntroducerProtocolState next(IntroducerAction a) {
		return this;
	}
}
