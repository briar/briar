package org.briarproject.api.keyagreement;

/** Manages tasks for conducting key agreements with remote peers. */
public interface KeyAgreementTaskFactory {

	/** Gets the current key agreement task. */
	KeyAgreementTask getTask();
}
