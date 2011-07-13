package net.sf.briar.db;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.Status;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;

/**
 * Abstract superclass containing code shared by ReadWriteLockDatabaseComponent
 * and SynchronizedDatabaseComponent.
 */
abstract class DatabaseComponentImpl<Txn> implements DatabaseComponent,
DatabaseCleaner.Callback {

	private static final Logger LOG =
		Logger.getLogger(DatabaseComponentImpl.class.getName());

	protected final Database<Txn> db;
	protected final DatabaseCleaner cleaner;

	private final Object spaceLock = new Object();
	private final Object writeLock = new Object();
	private long bytesStoredSinceLastCheck = 0L; // Locking: spaceLock
	private long timeOfLastCheck = 0L; // Locking: spaceLock
	private volatile boolean writesAllowed = true;

	DatabaseComponentImpl(Database<Txn> db, DatabaseCleaner cleaner) {
		this.db = db;
		this.cleaner = cleaner;
	}

	public void open(boolean resume) throws DbException {
		db.open(resume);
		cleaner.startCleaning();
	}

	/**
	 * Removes the oldest messages from the database, with a total size less
	 * than or equal to the given size.
	 */
	protected abstract void expireMessages(long size) throws DbException;

	/**
	 * Calculates and returns the sendability score of a message.
	 * <p>
	 * Locking: messages write.
	 */
	private int calculateSendability(Txn txn, Message m) throws DbException {
		int sendability = 0;
		// One point for a good rating
		if(db.getRating(txn, m.getAuthor()) == Rating.GOOD) sendability++;
		// One point per sendable child (backward inclusion)
		sendability += db.getNumberOfSendableChildren(txn, m.getId());
		return sendability;
	}

	public void checkFreeSpaceAndClean() throws DbException {
		long freeSpace = db.getFreeSpace();
		while(freeSpace < MIN_FREE_SPACE) {
			// If disk space is critical, disable the storage of new messages
			if(freeSpace < CRITICAL_FREE_SPACE) {
				if(LOG.isLoggable(Level.FINE)) LOG.fine("Critical cleanup");
				writesAllowed = false;
			} else {
				if(LOG.isLoggable(Level.FINE)) LOG.fine("Normal cleanup");
			}
			expireMessages(BYTES_PER_SWEEP);
			Thread.yield();
			freeSpace = db.getFreeSpace();
			// If disk space is no longer critical, re-enable writes
			if(freeSpace >= CRITICAL_FREE_SPACE && !writesAllowed) {
				writesAllowed = true;
				synchronized(writeLock) {
					writeLock.notifyAll();
				}
			}
		}
	}

	/**
	 * Returns true iff the database contains the given contact.
	 * <p>
	 * Locking: contacts read.
	 */
	protected boolean containsContact(ContactId c) throws DbException {
		Txn txn = db.startTransaction();
		try {
			boolean contains = db.containsContact(txn, c);
			db.commitTransaction(txn);
			return contains;
		} catch(DbException e) {
			db.abortTransaction(txn);
			throw e;
		}
	}

	/**
	 * Removes the given message (and all associated state) from the database. 
	 * <p>
	 * Locking: contacts read, messages write, messageStatuses write.
	 */
	protected void removeMessage(Txn txn, MessageId id) throws DbException {
		Integer sendability = db.getSendability(txn, id);
		assert sendability != null;
		// If the message is sendable, deleting it may affect its ancestors'
		// sendability (backward inclusion)
		if(sendability > 0) updateAncestorSendability(txn, id, false);
		db.removeMessage(txn, id);
	}

	public boolean shouldCheckFreeSpace() {
		synchronized(spaceLock) {
			long now = System.currentTimeMillis();
			if(bytesStoredSinceLastCheck > MAX_BYTES_BETWEEN_SPACE_CHECKS) {
				if(LOG.isLoggable(Level.FINE))
					LOG.fine(bytesStoredSinceLastCheck
						+ " bytes stored since last check");
				bytesStoredSinceLastCheck = 0L;
				timeOfLastCheck = now;
				return true;
			}
			if(now - timeOfLastCheck > MAX_MS_BETWEEN_SPACE_CHECKS) {
				if(LOG.isLoggable(Level.FINE))
					LOG.fine((now - timeOfLastCheck) + " ms since last check");
				bytesStoredSinceLastCheck = 0L;
				timeOfLastCheck = now;
				return true;
			}
		}
		return false;
	}

	/**
	 * If the given message is already in the database, marks it as seen by the
	 * sender and returns false. Otherwise stores the message, updates the
	 * sendability of its ancestors if necessary, marks the message as seen by
	 * the sender and unseen by all other contacts, and returns true.
	 * <p>
	 * Locking: contacts read, messages write, messageStatuses write.
	 */
	protected boolean storeMessage(Txn txn, Message m, ContactId sender)
	throws DbException {
		boolean added = db.addMessage(txn, m);
		// Mark the message as seen by the sender
		MessageId id = m.getId();
		if(sender != null) db.setStatus(txn, sender, id, Status.SEEN);
		if(added) {
			// Mark the message as unseen by other contacts
			for(ContactId c : db.getContacts(txn)) {
				if(!c.equals(sender)) db.setStatus(txn, c, id, Status.NEW);
			}
			// Calculate and store the message's sendability
			int sendability = calculateSendability(txn, m);
			db.setSendability(txn, id, sendability);
			if(sendability > 0) updateAncestorSendability(txn, id, true);
			// Count the bytes stored
			synchronized(spaceLock) {
				bytesStoredSinceLastCheck += m.getSize();
			}
		}
		return added;
	}

	/**
	 * Iteratively updates the sendability of a message's ancestors to reflect
	 * a change in the message's sendability. Returns the number of ancestors
	 * that have changed from sendable to not sendable, or vice versa.
	 * <p>
	 * Locking: messages write.
	 * @param increment True if the message's sendability has changed from 0 to
	 * greater than 0, or false if it has changed from greater than 0 to 0.
	 */
	private int updateAncestorSendability(Txn txn, MessageId m,
			boolean increment) throws DbException {
		int affected = 0;
		boolean changed = true;
		while(changed) {
			MessageId parent = db.getParent(txn, m);
			if(parent.equals(MessageId.NONE)) break;
			if(!db.containsMessage(txn, parent)) break;
			if(!db.getGroup(txn, m).equals(db.getGroup(txn, parent))) break;
			Integer parentSendability = db.getSendability(txn, parent);
			assert parentSendability != null;
			if(increment) {
				parentSendability++;
				changed = parentSendability == 1;
				if(changed) affected++;
			} else {
				assert parentSendability > 0;
				parentSendability--;
				changed = parentSendability == 0;
				if(changed) affected++;
			}
			db.setSendability(txn, parent, parentSendability);
			m = parent;
		}
		return affected;
	}

	/**
	 * Updates the sendability of all messages written by the given author, and
	 * the ancestors of those messages if necessary.
	 * <p>
	 * Locking: messages write.
	 * @param increment True if the user's rating for the author has changed
	 * from not good to good, or false if it has changed from good to not good.
	 */
	protected void updateAuthorSendability(Txn txn, AuthorId a,
			boolean increment) throws DbException {
		int direct = 0, indirect = 0;
		for(MessageId id : db.getMessagesByAuthor(txn, a)) {
			int sendability = db.getSendability(txn, id);
			if(increment) {
				db.setSendability(txn, id, sendability + 1);
				if(sendability == 0) {
					direct++;
					indirect += updateAncestorSendability(txn, id, true);
				}
			} else {
				assert sendability > 0;
				db.setSendability(txn, id, sendability - 1);
				if(sendability == 1) {
					direct++;
					indirect += updateAncestorSendability(txn, id, false);
				}
			}
		}
		if(LOG.isLoggable(Level.FINE))
			LOG.fine(direct + " messages affected directly, "
				+ indirect + " indirectly");
	}

	/**
	 * Blocks until messages are allowed to be stored in the database. The
	 * storage of messages is not allowed while the amount of free storage
	 * space available to the database is less than CRITICAL_FREE_SPACE.
	 */
	protected void waitForPermissionToWrite() {
		synchronized(writeLock) {
			while(!writesAllowed) {
				if(LOG.isLoggable(Level.FINE))
					LOG.fine("Waiting for permission to write");
				try {
					writeLock.wait();
				} catch(InterruptedException ignored) {}
			}
		}
	}
}
