package org.briarproject.api.plugins.simplex;

import org.briarproject.api.ContactId;
import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.plugins.TransportConnectionReader;
import org.briarproject.api.plugins.TransportConnectionWriter;

/** An interface for transport plugins that support simplex communication. */
public interface SimplexPlugin extends Plugin {

	/**
	 * Attempts to create and return a reader for the given contact using the
	 * current transport and configuration properties. Returns null if a reader
	 * could not be created.
	 */
	TransportConnectionReader createReader(ContactId c);

	/**
	 * Attempts to create and return a writer for the given contact using the
	 * current transport and configuration properties. Returns null if a writer
	 * could not be created.
	 */
	TransportConnectionWriter createWriter(ContactId c);
}
