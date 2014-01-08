package org.briarproject.api.plugins.simplex;

import org.briarproject.api.ContactId;
import org.briarproject.api.plugins.PluginCallback;

/**
 * An interface for handling readers and writers created by a simplex transport
 * plugin.
 */
public interface SimplexPluginCallback extends PluginCallback {

	void readerCreated(SimplexTransportReader r);

	void writerCreated(ContactId c, SimplexTransportWriter w);
}
