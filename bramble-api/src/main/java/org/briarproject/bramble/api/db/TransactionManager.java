package org.briarproject.bramble.api.db;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;

@NotNullByDefault
public interface TransactionManager {

	/**
	 * Starts a new transaction and returns an object representing it.
	 * <p/>
	 * This method acquires locks, so it must not be called while holding a
	 * lock.
	 *
	 * @param readOnly true if the transaction will only be used for reading.
	 */
	Transaction startTransaction(boolean readOnly) throws DbException;

	/**
	 * Commits a transaction to the database.
	 */
	void commitTransaction(Transaction txn) throws DbException;

	/**
	 * Ends a transaction. If the transaction has not been committed,
	 * it will be aborted. If the transaction has been committed,
	 * any events attached to the transaction are broadcast.
	 * The database lock will be released in either case.
	 */
	void endTransaction(Transaction txn);

	/**
	 * Runs the given task within a transaction.
	 */
	<E extends Exception> void transaction(boolean readOnly,
			DbRunnable<E> task) throws DbException, E;

	/**
	 * Runs the given task within a transaction and returns the result of the
	 * task.
	 */
	<R, E extends Exception> R transactionWithResult(boolean readOnly,
			DbCallable<R, E> task) throws DbException, E;

	/**
	 * Runs the given task within a transaction and returns the result of the
	 * task, which may be null.
	 */
	@Nullable
	<R, E extends Exception> R transactionWithNullableResult(boolean readOnly,
			NullableDbCallable<R, E> task) throws DbException, E;

}
