package org.briarproject.android.event;

import org.briarproject.api.event.Event;

public class PasswordValidateEvent extends Event {

	private final boolean passwordValidated;

	public PasswordValidateEvent(boolean passwordValidated) {
		this.passwordValidated = passwordValidated;
	}

	public boolean passwordValidated() {
		return passwordValidated;
	}
}
