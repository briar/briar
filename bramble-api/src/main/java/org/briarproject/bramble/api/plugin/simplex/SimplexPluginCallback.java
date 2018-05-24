package org.briarproject.bramble.api.plugin.simplex;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;

/**
 * An interface through which a simplex plugin interacts with the rest of the
 * application.
 */
@NotNullByDefault
public interface SimplexPluginCallback extends PluginCallback {

	void readerCreated(TransportConnectionReader r);

	void writerCreated(ContactId c, TransportConnectionWriter w);
}
