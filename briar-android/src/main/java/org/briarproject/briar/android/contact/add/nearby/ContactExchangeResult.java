package org.briarproject.briar.android.contact.add.nearby;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
abstract class ContactExchangeResult {

	static class Success extends ContactExchangeResult {
		final Author remoteAuthor;

		Success(Author remoteAuthor) {
			this.remoteAuthor = remoteAuthor;
		}
	}

	static class Error extends ContactExchangeResult {
		@Nullable
		final Author duplicateAuthor;

		Error(@Nullable Author duplicateAuthor) {
			this.duplicateAuthor = duplicateAuthor;
		}
	}

}
