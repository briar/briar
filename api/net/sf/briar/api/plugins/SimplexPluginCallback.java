package net.sf.briar.api.plugins;

import net.sf.briar.api.ContactId;

/**
 * An interface for receiving readers and writers created by a simplex plugin.
 */
public interface SimplexPluginCallback extends PluginCallback {

	void readerCreated(SimplexTransportReader r);

	void writerCreated(ContactId c, SimplexTransportWriter w);
}
