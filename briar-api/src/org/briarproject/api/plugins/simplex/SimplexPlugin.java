package org.briarproject.api.plugins.simplex;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.plugins.TransportConnectionReader;
import org.briarproject.api.plugins.TransportConnectionWriter;

import javax.annotation.Nullable;

/**
 * An interface for transport plugins that support simplex communication.
 */
@NotNullByDefault
public interface SimplexPlugin extends Plugin {

	/**
	 * Attempts to create and return a reader for the given contact using the
	 * current transport and configuration properties. Returns null if a reader
	 * cannot be created.
	 */
	@Nullable
	TransportConnectionReader createReader(ContactId c);

	/**
	 * Attempts to create and return a writer for the given contact using the
	 * current transport and configuration properties. Returns null if a writer
	 * cannot be created.
	 */
	@Nullable
	TransportConnectionWriter createWriter(ContactId c);
}
