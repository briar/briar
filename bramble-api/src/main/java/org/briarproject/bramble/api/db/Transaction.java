package org.briarproject.bramble.api.db;

import org.briarproject.bramble.api.event.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A wrapper around a database transaction. Transactions are not thread-safe.
 */
@NotThreadSafe
public class Transaction {

	private final Object txn;
	private final boolean readOnly;

	private List<Event> events = null;
	private boolean committed = false;

	public Transaction(Object txn, boolean readOnly) {
		this.txn = txn;
		this.readOnly = readOnly;
	}

	/**
	 * Returns the database transaction. The type of the returned object
	 * depends on the database implementation.
	 */
	public Object unbox() {
		return txn;
	}

	/**
	 * Returns true if the transaction can only be used for reading.
	 */
	public boolean isReadOnly() {
		return readOnly;
	}

	/**
	 * Attaches an event to be broadcast when the transaction has been
	 * committed.
	 */
	public void attach(Event e) {
		if (events == null) events = new ArrayList<Event>();
		events.add(e);
	}

	/**
	 * Returns any events attached to the transaction.
	 */
	public List<Event> getEvents() {
		if (events == null) return Collections.emptyList();
		return events;
	}

	/**
	 * Returns true if the transaction has been committed.
	 */
	public boolean isCommitted() {
		return committed;
	}

	/**
	 * Marks the transaction as committed. This method should only be called
	 * by the DatabaseComponent. It must not be called more than once.
	 */
	public void setCommitted() {
		if (committed) throw new IllegalStateException();
		committed = true;
	}
}
