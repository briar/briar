package net.sf.briar.api.plugins.simplex;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.plugins.PluginCallback;

/**
 * An interface for handling readers and writers created by a simplex transport
 * plugin.
 */
public interface SimplexPluginCallback extends PluginCallback {

	void readerCreated(SimplexTransportReader r);

	void writerCreated(ContactId c, SimplexTransportWriter w);
}
