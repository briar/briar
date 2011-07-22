package net.sf.briar.api.protocol;

import java.io.IOException;

/** An interface for creating a subscription update. */
public interface SubscriptionWriter {

	// FIXME: This should work with groups, not IDs
	/** Sets the contents of the update. */
	void setSubscriptions(Iterable<GroupId> subs) throws IOException;
}
