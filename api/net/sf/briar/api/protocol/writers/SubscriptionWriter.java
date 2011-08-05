package net.sf.briar.api.protocol.writers;

import java.io.IOException;
import java.util.Map;

import net.sf.briar.api.protocol.Group;

/** An interface for creating a subscription update. */
public interface SubscriptionWriter {

	/** Writes the contents of the update. */
	void writeSubscriptions(Map<Group, Long> subs) throws IOException;
}
