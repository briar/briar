package net.sf.briar.api.db.event;

/** An interface for receiving notifications when database events occur. */
public interface DatabaseListener {

	void eventOccurred(DatabaseEvent e);
}
