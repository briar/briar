package org.briarproject.briar.api.privategroup;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class JoinMessageHeader extends GroupMessageHeader {

	private final Visibility visibility;
	private final boolean isInitial;

	public JoinMessageHeader(GroupMessageHeader h, Visibility visibility,
			boolean isInitial) {
		super(h.getGroupId(), h.getId(), h.getParentId(), h.getTimestamp(),
				h.getAuthor(), h.getAuthorStatus(), h.isRead());
		this.visibility = visibility;
		this.isInitial = isInitial;
	}

	public Visibility getVisibility() {
		return visibility;
	}

	public boolean isInitial() {
		return isInitial;
	}

}
