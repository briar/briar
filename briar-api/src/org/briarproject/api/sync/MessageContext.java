package org.briarproject.api.sync;

import org.briarproject.api.db.Metadata;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class MessageContext extends BaseMessageContext {

	private final Metadata metadata;

	public MessageContext(@NotNull Metadata metadata,
			@NotNull Collection<MessageId> dependencies) {

		super(dependencies);
		this.metadata = metadata;
	}

	public MessageContext(@NotNull Metadata metadata) {
		this(metadata, Collections.<MessageId>emptyList());
	}

	public Metadata getMetadata() {
		return metadata;
	}

}
