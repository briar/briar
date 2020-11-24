package org.briarproject.briar.api.identity;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class AuthorInfo {

	public enum Status {
		NONE, ANONYMOUS, UNKNOWN, UNVERIFIED, VERIFIED, OURSELVES;

		public boolean isContact() {
			return this == UNVERIFIED || this == VERIFIED;
		}
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

	@Override
	public int hashCode() {
		int hashCode = status.ordinal();
		if (alias != null) hashCode += alias.hashCode();
		return hashCode;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof AuthorInfo)) return false;
		AuthorInfo info = (AuthorInfo) o;
		//noinspection EqualsReplaceableByObjectsCall
		return status == info.status &&
				(alias == null ? info.alias == null : alias.equals(info.alias));
	}
}
