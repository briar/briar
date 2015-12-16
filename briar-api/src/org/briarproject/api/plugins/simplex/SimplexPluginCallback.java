package org.briarproject.api.plugins.simplex;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.plugins.PluginCallback;
import org.briarproject.api.plugins.TransportConnectionReader;
import org.briarproject.api.plugins.TransportConnectionWriter;

/**
 * An interface for handling readers and writers created by a simplex transport
 * plugin.
 */
public interface SimplexPluginCallback extends PluginCallback {

	void readerCreated(TransportConnectionReader r);

	void writerCreated(ContactId c, TransportConnectionWriter w);
}
