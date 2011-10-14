package net.sf.briar.api.db;

/** An interface for receiving notifications when database events occur. */
public interface DatabaseListener {

	static enum Event {
		ACKS_ADDED,
		CONTACTS_UPDATED,
		MESSAGES_ADDED,
		SUBSCRIPTIONS_UPDATED,
		TRANSPORTS_UPDATED
	};

	void eventOccurred(Event e);
}
