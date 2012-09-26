package net.sf.briar.api.plugins.simplex;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.plugins.Plugin;

/** An interface for transport plugins that support simplex communication. */
public interface SimplexPlugin extends Plugin {

	/**
	 * Attempts to create and return a reader for the given contact using the
	 * current transport and configuration properties. Returns null if a reader
	 * could not be created.
	 */
	SimplexTransportReader createReader(ContactId c);

	/**
	 * Attempts to create and return a writer for the given contact using the
	 * current transport and configuration properties. Returns null if a writer
	 * could not be created.
	 */
	SimplexTransportWriter createWriter(ContactId c);
}
