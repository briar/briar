package org.briarproject.bramble.api.keyagreement;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

/**
 * Manages tasks for conducting key agreements with remote peers.
 */
@NotNullByDefault
public interface KeyAgreementTaskFactory {

	/**
	 * Gets the current key agreement task.
	 */
	KeyAgreementTask createTask();
}
