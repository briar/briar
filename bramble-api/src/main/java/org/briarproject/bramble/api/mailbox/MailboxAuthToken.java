package org.briarproject.bramble.api.mailbox;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
@NotNullByDefault
public class MailboxAuthToken extends MailboxId {
	public MailboxAuthToken(byte[] id) {
		super(id);
	}

	/**
	 * Creates a {@link MailboxAuthToken} from the given string.
	 *
	 * @throws InvalidMailboxIdException if token is not valid.
	 */
	public static MailboxAuthToken fromString(@Nullable String token)
			throws InvalidMailboxIdException {
		return new MailboxAuthToken(bytesFromString(token));
	}
}
