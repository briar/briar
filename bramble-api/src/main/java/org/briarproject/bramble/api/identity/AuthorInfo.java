package org.briarproject.bramble.api.identity;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class AuthorInfo {

	public enum Status {
		NONE, ANONYMOUS, UNKNOWN, UNVERIFIED, VERIFIED, OURSELVES
	}

	private final Status status;
	@Nullable
	private final String alias;

	public AuthorInfo(Status status, @Nullable String alias) {
		this.status = status;
		this.alias = alias;
	}

	public AuthorInfo(Status status) {
		this(status, null);
	}

	public Status getStatus() {
		return status;
	}

	@Nullable
	public String getAlias() {
		return alias;
	}

}
