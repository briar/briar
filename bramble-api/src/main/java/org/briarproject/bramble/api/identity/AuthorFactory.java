package org.briarproject.bramble.api.identity;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface AuthorFactory {

	Author createAuthor(String name, byte[] publicKey);

	LocalAuthor createLocalAuthor(String name, byte[] publicKey,
			byte[] privateKey);
}
