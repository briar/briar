package net.sf.briar.api.protocol.writers;

import java.io.IOException;

import net.sf.briar.api.protocol.Group;

/** An interface for creating a subscription update. */
public interface SubscriptionWriter {

	/** Sets the contents of the update. */
	void setSubscriptions(Iterable<Group> subs) throws IOException;
}
