package org.briarproject.briar.api.introduction;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;

import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_ABORT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_ACK;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_REQUEST;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_RESPONSE;

@NotNullByDefault
public enum IntroducerAction {

	LOCAL_REQUEST,
	LOCAL_ABORT,
	REMOTE_ACCEPT_1,
	REMOTE_ACCEPT_2,
	REMOTE_DECLINE_1,
	REMOTE_DECLINE_2,
	REMOTE_ABORT,
	ACK_1,
	ACK_2;

	@Nullable
	public static IntroducerAction getLocal(int type) {
		if (type == TYPE_REQUEST) return LOCAL_REQUEST;
		if (type == TYPE_ABORT) return LOCAL_ABORT;
		return null;
	}

	@Nullable
	public static IntroducerAction getRemote(int type, boolean one,
			boolean accept) {

		if (one) {
			if (type == TYPE_RESPONSE && accept) return REMOTE_ACCEPT_1;
			if (type == TYPE_RESPONSE) return REMOTE_DECLINE_1;
			if (type == TYPE_ACK) return ACK_1;
		} else {
			if (type == TYPE_RESPONSE && accept) return REMOTE_ACCEPT_2;
			if (type == TYPE_RESPONSE) return REMOTE_DECLINE_2;
			if (type == TYPE_ACK) return ACK_2;
		}
		if (type == TYPE_ABORT) return REMOTE_ABORT;
		return null;
	}

	@Nullable
	public static IntroducerAction getRemote(int type, boolean one) {
		return getRemote(type, one, true);
	}

}
