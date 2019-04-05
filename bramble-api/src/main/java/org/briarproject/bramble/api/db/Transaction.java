package org.briarproject.bramble.api.db;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventExecutor;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

import static java.util.Collections.emptyList;

/**
 * A wrapper around a database transaction. Transactions are not thread-safe.
 */
@NotThreadSafe
public class Transaction {

	private final Object txn;
	private final boolean readOnly;

	private List<CommitAction> actions = null;
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
	 * committed. The event will be broadcast on the {@link EventExecutor}.
	 */
	public void attach(Event e) {
		if (actions == null) actions = new ArrayList<>();
		actions.add(new EventAction(e));
	}

	/**
	 * Attaches a task to be executed when the transaction has been
	 * committed. The task will be run on the {@link EventExecutor}.
	 */
	public void attach(Runnable r) {
		if (actions == null) actions = new ArrayList<>();
		actions.add(new TaskAction(r));
	}

	/**
	 * Returns any actions attached to the transaction.
	 */
	public List<CommitAction> getActions() {
		return actions == null ? emptyList() : actions;
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
