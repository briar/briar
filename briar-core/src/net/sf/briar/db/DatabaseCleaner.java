package net.sf.briar.db;

import net.sf.briar.api.db.DbException;

interface DatabaseCleaner {

	/**
	 * Starts a new thread to monitor the amount of free storage space
	 * available to the database and expire old messages as necessary. The
	 * cleaner will pause for the given number of milliseconds between sweeps.
	 */
	void startCleaning(Callback callback, long msBetweenSweeps);

	/** Tells the cleaner thread to exit. */
	void stopCleaning();

	interface Callback {

		/**
		 * Checks how much free storage space is available to the database, and
		 * if necessary expires old messages until the free space is at least
		 * DatabaseConstants.MIN_FREE_SPACE. If the free space is less than
		 * DatabaseConstants.CRITICAL_FREE_SPACE and there are no more messages
		 * to expire, an Error will be thrown.
		 */
		void checkFreeSpaceAndClean() throws DbException;

		/**
		 * Returns true if the amount of free storage space available to the
		 * database should be checked.
		 */
		boolean shouldCheckFreeSpace();
	}
}
