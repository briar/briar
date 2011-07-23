package net.sf.briar.api.protocol;

import java.io.IOException;

/** An interface for creating a subscription update. */
public interface SubscriptionWriter {

	/** Sets the contents of the update. */
	void setSubscriptions(Iterable<Group> subs) throws IOException;
}
