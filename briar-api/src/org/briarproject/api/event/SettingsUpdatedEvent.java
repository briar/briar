package org.briarproject.api.event;

/** An event that is broadcast when one or more settings are updated. */
public class SettingsUpdatedEvent extends Event {

	private final String namespace;

	public SettingsUpdatedEvent(String namespace) {
		this.namespace = namespace;
	}

	public String getNamespace() {
		return namespace;
	}
}
