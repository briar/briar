package net.sf.briar.db;

import net.sf.briar.api.db.DbException;

interface DatabaseCleaner {

	/**
	 * Starts a new thread to monitor the amount of free storage space
	 * available to the database and expire old messages as necessary.
	 */
	void startCleaning();

	/** Tells the cleaner thread to exit and returns when it has done so. */
	void stopCleaning();

	interface Callback {

		/**
		 * Checks how much free storage space is available to the database, and if
		 * necessary expires old messages until the free space is at least
		 * MIN_FREE_SPACE. While the free space is less than CRITICAL_FREE_SPACE,
		 * operations that attempt to store messages in the database will block.
		 */
		void checkFreeSpaceAndClean() throws DbException;

		/**
		 * Called by the cleaner; returns true iff the amount of free storage space
		 * available to the database should be checked.
		 */
		boolean shouldCheckFreeSpace();
	}
}
