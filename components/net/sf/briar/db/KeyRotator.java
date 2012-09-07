package net.sf.briar.db;

import net.sf.briar.api.db.DbException;

interface KeyRotator {

	/**
	 * Starts a new thread to rotate keys periodically. The rotator will pause
	 * for the given number of milliseconds between rotations.
	 */
	void startRotating(Callback callback, long msBetweenRotations);

	/** Tells the rotator thread to exit. */
	void stopRotating();

	interface Callback {

		/**
		 * Rotates keys, replacing and destroying any keys that have passed the
		 * ends of their respective retention periods.
		 */
		void rotateKeys() throws DbException;
	}
}
