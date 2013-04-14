package net.sf.briar.android.blogs;

import net.sf.briar.api.messaging.LocalGroup;

class LocalGroupItem {

	static final LocalGroupItem NEW = new LocalGroupItem(null);

	private final LocalGroup localGroup;

	LocalGroupItem(LocalGroup localGroup) {
		this.localGroup = localGroup;
	}

	LocalGroup getLocalGroup() {
		return localGroup;
	}
}
