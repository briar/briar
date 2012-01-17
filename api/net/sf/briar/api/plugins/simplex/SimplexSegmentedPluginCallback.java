package net.sf.briar.api.plugins.simplex;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.plugins.PluginCallback;

/**
 * An interface for handling readers and writers created by a simplex
 * segmented transport plugin.
 */
public interface SimplexSegmentedPluginCallback extends PluginCallback {

	void readerCreated(SimplexSegmentedTransportReader r);

	void writerCreated(ContactId c, SimplexSegmentedTransportWriter w);

}
