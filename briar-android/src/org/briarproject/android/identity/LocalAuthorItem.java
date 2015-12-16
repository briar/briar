package org.briarproject.android.identity;

import org.briarproject.api.identity.LocalAuthor;

public class LocalAuthorItem {

	public static final LocalAuthorItem ANONYMOUS = new LocalAuthorItem(null);
	public static final LocalAuthorItem NEW = new LocalAuthorItem(null);

	private final LocalAuthor localAuthor;

	public LocalAuthorItem(LocalAuthor localAuthor) {
		this.localAuthor = localAuthor;
	}

	public LocalAuthor getLocalAuthor() {
		return localAuthor;
	}
}
