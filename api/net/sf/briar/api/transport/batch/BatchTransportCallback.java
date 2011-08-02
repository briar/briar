package net.sf.briar.api.transport.batch;

/**
 * An interface for receiving readers and writers created by a  batch-mode
 * transport plugin.
 */
public interface BatchTransportCallback {

	void readerCreated(BatchTransportReader r);

	void writerCreated(BatchTransportWriter w);
}
