package org.briarproject.api.sharing;

import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.GroupId;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

@Immutable
@NotNullByDefault
public abstract class InvitationItem {

	private final Shareable shareable;
	private final boolean subscribed;

	public InvitationItem(Shareable shareable, boolean subscribed) {
		this.shareable = shareable;
		this.subscribed = subscribed;
	}

	public Shareable getShareable() {
		return shareable;
	}

	public GroupId getId() {
		return shareable.getId();
	}

	public String getName() {
		return shareable.getName();
	}

	public boolean isSubscribed() {
		return subscribed;
	}

}
