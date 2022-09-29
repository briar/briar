package org.briarproject.bramble.api.mailbox;

import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class MailboxVersion implements Comparable<MailboxVersion> {

	private final int major;
	private final int minor;

	public MailboxVersion(int major, int minor) {
		this.major = major;
		this.minor = minor;
	}

	public int getMajor() {
		return major;
	}

	public int getMinor() {
		return minor;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof MailboxVersion) {
			MailboxVersion v = (MailboxVersion) o;
			return major == v.major && minor == v.minor;
		}
		return false;
	}

	@Override
	public int compareTo(MailboxVersion v) {
		int c = major - v.major;
		if (c != 0) {
			return c;
		}
		return minor - v.minor;
	}
}
