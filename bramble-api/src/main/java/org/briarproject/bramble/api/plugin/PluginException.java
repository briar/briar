package org.briarproject.bramble.api.plugin;

/**
 * An exception that indicates an error starting or stopping a {@link Plugin}.
 */
public class PluginException extends Exception {

	public PluginException() {
		super();
	}

	public PluginException(Throwable cause) {
		super(cause);
	}
}
