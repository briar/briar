package org.briarproject.api.privategroup;

import org.briarproject.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class JoinMessageHeader extends GroupMessageHeader {

	private final Visibility visibility;
	private final boolean isCreator;

	public JoinMessageHeader(GroupMessageHeader h, Visibility visibility, boolean isCreator) {
		super(h.getGroupId(), h.getId(), h.getParentId(), h.getTimestamp(),
				h.getAuthor(), h.getAuthorStatus(), h.isRead());
		this.visibility = visibility;
		this.isCreator = isCreator;
	}

	public Visibility getVisibility() {
		return visibility;
	}

	public boolean isInitial() {
		return isCreator;
	}

}
