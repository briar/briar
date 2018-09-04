package org.briarproject.briar.api.introduction;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.messaging.Nameable;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.briar.api.introduction.Role.INTRODUCER;

@Immutable
@NotNullByDefault
public class Introduction implements Nameable {

	private final Author introducedAuthor;
	private final Role ourRole;

	public Introduction(Author introducedAuthor, Role ourRole) {
		this.introducedAuthor = introducedAuthor;
		this.ourRole = ourRole;
	}

	@Override
	public String getName() {
		return introducedAuthor.getName();
	}

	public boolean isIntroducer() {
		return ourRole == INTRODUCER;
	}

}
