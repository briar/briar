package net.sf.briar.api.transport.batch;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.transport.TransportCallback;

/**
 * An interface for receiving readers and writers created by a batch-mode
 * transport plugin.
 */
public interface BatchTransportCallback extends TransportCallback {

	void readerCreated(BatchTransportReader r);

	void writerCreated(ContactId contactId, BatchTransportWriter w);
}
