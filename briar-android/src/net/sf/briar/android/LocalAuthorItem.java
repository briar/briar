package net.sf.briar.android;

import net.sf.briar.api.LocalAuthor;

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
