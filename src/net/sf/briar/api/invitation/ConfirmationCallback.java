package net.sf.briar.api.invitation;

/** An interface for informing a peer of whether confirmation codes match. */
public interface ConfirmationCallback {

	/**  Called to indicate that the confirmation codes match. */
	void codesMatch();

	/**
	 * Called to indicate that either the confirmation codes do not match or
	 * the result of the comparison is unknown.
	 */
	void codesDoNotMatch();
}
