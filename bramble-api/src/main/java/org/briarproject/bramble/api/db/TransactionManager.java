package org.briarproject.bramble.api.db;

import org.briarproject.bramble.api.event.EventExecutor;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * An interface for managing database transactions.
 * <p>
 * Read-only transactions may access the database concurrently. Read-write
 * transactions access the database exclusively, so starting a read-only or
 * read-write transaction will block until there are no read-write
 * transactions in progress.
 * <p>
 * Failing to {@link #endTransaction(Transaction) end} a transaction will
 * prevent other callers from accessing the database, so it is recommended to
 * use the {@link #transaction(boolean, DbRunnable)},
 * {@link #transactionWithResult(boolean, DbCallable)} and
 * {@link #transactionWithNullableResult(boolean, NullableDbCallable)} methods
 * where possible, which handle committing or aborting the transaction on the
 * caller's behalf.
 * <p>
 * Transactions are not reentrant, i.e. it is not permitted to start a
 * transaction on a thread that already has a transaction in progress.
 */
@ThreadSafe
@NotNullByDefault
public interface TransactionManager {

	/**
	 * Starts a new transaction and returns an object representing it. This
	 * method acquires the database lock, which is held until
	 * {@link #endTransaction(Transaction)} is called.
	 *
	 * @param readOnly True if the transaction will only be used for reading,
	 * in which case the database lock can be shared with other read-only
	 * transactions.
	 */
	Transaction startTransaction(boolean readOnly) throws DbException;

	/**
	 * Commits a transaction to the database.
	 * {@link #endTransaction(Transaction)} must be called to release the
	 * database lock.
	 */
	void commitTransaction(Transaction txn) throws DbException;

	/**
	 * Ends a transaction. If the transaction has not been committed by
	 * calling {@link #commitTransaction(Transaction)}, it is aborted and the
	 * database lock is released.
	 * <p>
	 * If the transaction has been committed, any
	 * {@link Transaction#attach events} attached to the transaction are
	 * broadcast and any {@link Transaction#attach(Runnable) tasks} attached
	 * to the transaction are submitted to the {@link EventExecutor}. The
	 * database lock is then released.
	 */
	void endTransaction(Transaction txn);

	/**
	 * Runs the given task within a transaction. The database lock is held
	 * while running the task.
	 *
	 * @param readOnly True if the transaction will only be used for reading,
	 * in which case the database lock can be shared with other read-only
	 * transactions.
	 */
	<E extends Exception> void transaction(boolean readOnly,
			DbRunnable<E> task) throws DbException, E;

	/**
	 * Runs the given task within a transaction and returns the result of the
	 * task. The database lock is held while running the task.
	 *
	 * @param readOnly True if the transaction will only be used for reading,
	 * in which case the database lock can be shared with other read-only
	 * transactions.
	 */
	<R, E extends Exception> R transactionWithResult(boolean readOnly,
			DbCallable<R, E> task) throws DbException, E;

	/**
	 * Runs the given task within a transaction and returns the result of the
	 * task, which may be null. The database lock is held while running the
	 * task.
	 *
	 * @param readOnly True if the transaction will only be used for reading,
	 * in which case the database lock can be shared with other read-only
	 * transactions.
	 */
	@Nullable
	<R, E extends Exception> R transactionWithNullableResult(boolean readOnly,
			NullableDbCallable<R, E> task) throws DbException, E;

}
