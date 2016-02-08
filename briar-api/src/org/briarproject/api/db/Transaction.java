package org.briarproject.api.db;

/** A wrapper around a database transaction. */
public class Transaction {

	private final Object txn;

	public Transaction(Object txn) {
		this.txn = txn;
	}

	public Object unbox() {
		return txn;
	}
}
