package org.briarproject.bramble.api.mailbox;

import com.fasterxml.jackson.annotation.JsonValue;

import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Locale;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import static org.briarproject.bramble.util.StringUtils.fromHexString;
import static org.briarproject.bramble.util.StringUtils.toHexString;

@ThreadSafe
@NotNullByDefault
public abstract class MailboxId extends UniqueId {
	MailboxId(byte[] id) {
		super(id);
	}

	/**
	 * Returns valid {@link MailboxId} bytes from the given string.
	 *
	 * @throws InvalidMailboxIdException if token is not valid.
	 */
	static byte[] bytesFromString(@Nullable String token)
			throws InvalidMailboxIdException {
		if (token == null || token.length() != 64) {
			throw new InvalidMailboxIdException();
		}
		try {
			return fromHexString(token);
		} catch (IllegalArgumentException e) {
			throw new InvalidMailboxIdException();
		}
	}

	/**
	 * Returns the string representation expected by the mailbox API.
	 * Also used for serialization.
	 */
	@Override
	@JsonValue
	public String toString() {
		return toHexString(getBytes()).toLowerCase(Locale.US);
	}
}
