package org.briarproject.api.sync;

import org.briarproject.api.db.Metadata;

import java.util.Collection;

public class MessageContext extends BaseMessageContext {

	private final Metadata metadata;

	public MessageContext(Metadata metadata,
			Collection<MessageId> dependencies) {

		super(dependencies);
		this.metadata = metadata;
	}

	public MessageContext(Metadata metadata) {
		this(metadata, null);
	}

	public Metadata getMetadata() {
		return metadata;
	}

}
