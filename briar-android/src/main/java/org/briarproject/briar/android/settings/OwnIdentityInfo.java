package org.briarproject.briar.android.settings;

import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.identity.AuthorInfo;

@NotNullByDefault
class OwnIdentityInfo {

	private final LocalAuthor localAuthor;
	private final AuthorInfo authorInfo;

	OwnIdentityInfo(LocalAuthor localAuthor, AuthorInfo authorInfo) {
		this.localAuthor = localAuthor;
		this.authorInfo = authorInfo;
	}

	LocalAuthor getLocalAuthor() {
		return localAuthor;
	}

	AuthorInfo getAuthorInfo() {
		return authorInfo;
	}

}