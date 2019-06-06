package org.briarproject.bramble.api.rendezvous;

import org.briarproject.bramble.api.plugin.TransportId;

/**
 * Interface for the poller that makes rendezvous connections to pending
 * contacts.
 */
public interface RendezvousPoller {

	long getLastPollTime(TransportId t);
}
