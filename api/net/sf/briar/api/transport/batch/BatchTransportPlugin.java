package net.sf.briar.api.transport.batch;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.transport.TransportPlugin;

/**
 * An interface for transport plugins that do not support bidirectional,
 * reliable, ordered, timely delivery of data.
 */
public interface BatchTransportPlugin extends TransportPlugin {

	/**
	 * Attempts to create and return a BatchTransportReader for the given
	 * contact using the current transport and configuration properties.
	 * Returns null if a reader could not be created.
	 */
	BatchTransportReader createReader(ContactId c);

	/**
	 * Attempts to create and return a BatchTransportWriter for the given
	 * contact using the current transport and configuration properties.
	 * Returns null if a writer could not be created.
	 */
	BatchTransportWriter createWriter(ContactId c);
}
