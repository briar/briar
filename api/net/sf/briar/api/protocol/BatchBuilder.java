package net.sf.briar.api.protocol;

public interface BatchBuilder {

	/** Adds a message to the batch. */
	void addMessage(Message m);

	/** Builds and returns the batch. */
	Batch build();
}
