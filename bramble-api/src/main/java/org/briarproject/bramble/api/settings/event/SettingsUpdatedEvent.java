package org.briarproject.bramble.api.settings.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.settings.Settings;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when one or more settings are updated.
 */
@Immutable
@NotNullByDefault
public class SettingsUpdatedEvent extends Event {

	private final String namespace;
	private final Settings settings;

	public SettingsUpdatedEvent(String namespace, Settings settings) {
		this.namespace = namespace;
		this.settings = settings;
	}

	public String getNamespace() {
		return namespace;
	}

	public Settings getSettings() {
		return settings;
	}
}
