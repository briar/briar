package net.sf.briar.api.db;

/**
 * An interface for receiving notifications when the database may have new
 * messages available.
 */
public interface MessageListener {

	void messagesAdded();
}
