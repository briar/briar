package net.sf.briar.api.transport.batch;

import java.util.Map;

/**
 * An interface for receiving readers and writers created by a batch-mode
 * transport plugin.
 */
public interface BatchTransportCallback {

	void readerCreated(BatchTransportReader r);

	void writerCreated(BatchTransportWriter w);

	void setLocalTransports(Map<String, String> transports);

	void setConfig(Map<String, String> config);
}
