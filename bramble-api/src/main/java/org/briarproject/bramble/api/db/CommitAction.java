package org.briarproject.bramble.api.db;

import org.briarproject.bramble.api.event.EventExecutor;

/**
 * An action that's taken when a {@link Transaction} is committed.
 */
public interface CommitAction {

	void accept(Visitor visitor);

	interface Visitor {

		@EventExecutor
		void visit(EventAction a);

		@EventExecutor
		void visit(TaskAction a);
	}
}
