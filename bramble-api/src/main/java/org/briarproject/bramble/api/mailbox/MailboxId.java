package org.briarproject.bramble.api.mailbox;

import com.fasterxml.jackson.annotation.JsonValue;

import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Locale;

import javax.annotation.concurrent.ThreadSafe;

import static org.briarproject.bramble.util.StringUtils.fromHexString;
import static org.briarproject.bramble.util.StringUtils.toHexString;

@ThreadSafe
@NotNullByDefault
public class MailboxId extends UniqueId {

	public MailboxId(byte[] id) {
		super(id);
	}

	/**
	 * Creates a {@link MailboxId} from the given string.
	 *
	 * @throws IllegalArgumentException if token is not valid.
	 */
	public static MailboxId fromString(String token) {
		if (token.length() != 64) throw new IllegalArgumentException();
		return new MailboxId(fromHexString(token));
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

	@Override
	public boolean equals(Object o) {
		return o instanceof MailboxId && super.equals(o);
	}
}
