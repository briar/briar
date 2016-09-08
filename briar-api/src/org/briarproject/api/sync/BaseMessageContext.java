package org.briarproject.api.sync;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public abstract class BaseMessageContext {

	private final Collection<MessageId> dependencies;

	public BaseMessageContext(@NotNull Collection<MessageId> dependencies) {
		this.dependencies = dependencies;
	}

	public Collection<MessageId> getDependencies() {
		return dependencies;
	}

}
