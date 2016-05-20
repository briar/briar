package org.briarproject.api.sync;

import java.util.Collection;

public abstract class BaseMessageContext {

	private final Collection<MessageId> dependencies;

	public BaseMessageContext(Collection<MessageId> dependencies) {
		this.dependencies = dependencies;
	}

	public Collection<MessageId> getDependencies() {
		return dependencies;
	}

}
