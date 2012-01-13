package net.sf.briar.api.plugins.simplex;

import net.sf.briar.api.ContactId;

/**
 * An interface for handling readers and writers created by a simplex
 * segmented transport plugin.
 */
public interface SimplexSegmentedPluginCallback {

	void readerCreated(SimplexSegmentedTransportReader r);

	void writerCreated(ContactId c, SimplexSegmentedTransportWriter w);

}
