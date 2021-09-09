package org.briarproject.briar.api.privategroup;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class JoinMessageHeader extends GroupMessageHeader {

	private final boolean isInitial;

	public JoinMessageHeader(GroupMessageHeader h, boolean isInitial) {
		super(h.getGroupId(), h.getId(), h.getParentId(), h.getTimestamp(),
				h.getAuthor(), h.getAuthorInfo(), h.isRead());
		this.isInitial = isInitial;
	}

	public boolean isInitial() {
		return isInitial;
	}

}
