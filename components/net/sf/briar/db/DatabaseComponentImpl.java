package net.sf.briar.db;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.NeighbourId;
import net.sf.briar.api.db.Rating;
import net.sf.briar.api.db.Status;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;

import com.google.inject.Provider;

abstract class DatabaseComponentImpl<Txn> implements DatabaseComponent {

	private static final Logger LOG =
		Logger.getLogger(DatabaseComponentImpl.class.getName());

	protected final Database<Txn> db;
	protected final Provider<Batch> batchProvider;

	private final Object spaceLock = new Object();
	private final Object writeLock = new Object();
	private long bytesStoredSinceLastCheck = 0L; // Locking: spaceLock
	private long timeOfLastCheck = 0L; // Locking: spaceLock
	private volatile boolean writesAllowed = true;

	DatabaseComponentImpl(Database<Txn> db, Provider<Batch> batchProvider) {
		this.db = db;
		this.batchProvider = batchProvider;
		startCleaner();
	}

	protected abstract void expireMessages(long size) throws DbException;

	// Locking: messages write
	private int calculateSendability(Txn txn, Message m) throws DbException {
		int sendability = 0;
		// One point for a good rating
		if(getRating(m.getAuthor()) == Rating.GOOD) sendability++;
		// One point per sendable child (backward inclusion)
		for(MessageId kid : db.getMessagesByParent(txn, m.getId())) {
			Integer kidSendability = db.getSendability(txn, kid);
			assert kidSendability != null;
			if(kidSendability > 0) sendability++;
		}
		return sendability;
	}

	private void checkFreeSpaceAndClean() throws DbException {
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

	// Locking: contacts read
	protected boolean containsNeighbour(NeighbourId n) throws DbException {
		Txn txn = db.startTransaction();
		try {
			boolean contains = db.containsNeighbour(txn, n);
			db.commitTransaction(txn);
			return contains;
		} catch(DbException e) {
			db.abortTransaction(txn);
			throw e;
		}
	}

	// Locking: contacts read, messages write, messageStatuses write
	protected void removeMessage(Txn txn, MessageId id) throws DbException {
		Integer sendability = db.getSendability(txn, id);
		assert sendability != null;
		if(sendability > 0) updateAncestorSendability(txn, id, false);
		db.removeMessage(txn, id);
	}

	private boolean shouldCheckFreeSpace() {
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

	private void startCleaner() {
		Runnable cleaner = new Runnable() {
			public void run() {
				try {
					while(true) {
						if(shouldCheckFreeSpace()) {
							checkFreeSpaceAndClean();
						} else {
							try {
								Thread.sleep(CLEANER_SLEEP_MS);
							} catch(InterruptedException ignored) {}
						}
					}
				} catch(Throwable t) {
					// FIXME: Work out what to do here
					t.printStackTrace();
					System.exit(1);
				}
			}
		};
		new Thread(cleaner).start();
	}

	// Locking: contacts read, messages write, messageStatuses write
	protected boolean storeMessage(Txn txn, Message m, NeighbourId sender)
	throws DbException {
		boolean added = db.addMessage(txn, m);
		// Mark the message as seen by the sender
		MessageId id = m.getId();
		if(sender != null) db.setStatus(txn, sender, id, Status.SEEN);
		if(added) {
			// Mark the message as unseen by other neighbours
			for(NeighbourId n : db.getNeighbours(txn)) {
				if(!n.equals(sender)) db.setStatus(txn, n, id, Status.NEW);
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

	// Locking: messages write
	private int updateAncestorSendability(Txn txn, MessageId m,
			boolean increment) throws DbException {
		int affected = 0;
		boolean changed = true;
		while(changed) {
			MessageId parent = db.getParent(txn, m);
			if(parent.equals(MessageId.NONE)) break;
			if(!db.containsMessage(txn, parent)) break;
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

	// Locking: messages write
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
