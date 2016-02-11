package org.briarproject.api.db;

/**
 * A wrapper around a database transaction. Transactions are not thread-safe.
 */
public class Transaction {

	private final Object txn;
	private boolean complete = false;

	public Transaction(Object txn) {
		this.txn = txn;
	}

	/**
	 * Returns the database transaction. The type of the returned object
	 * depends on the database implementation.
	 */
	public Object unbox() {
		return txn;
	}

	/**
	 * Returns true if the transaction is ready to be committed.
	 */
	public boolean isComplete() {
		return complete;
	}

	/**
	 * Marks the transaction as ready to be committed. This method must not be
	 * called more than once.
	 */
	public void setComplete() {
		if (complete) throw new IllegalStateException();
		complete = true;
	}
}
