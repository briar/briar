package net.sf.briar.api.protocol.writers;

import java.io.IOException;
import java.util.Collection;

import net.sf.briar.api.protocol.Group;

/** An interface for creating a subscription update. */
public interface SubscriptionWriter {

	/** Writes the contents of the update. */
	void writeSubscriptions(Collection<Group> subs) throws IOException;
}
