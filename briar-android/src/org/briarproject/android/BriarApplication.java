package org.briarproject.android;

/**
 * This exists so that the Application object will not necessarily be cast
 * directly to the Briar application object.
 */
public interface BriarApplication {

	AndroidComponent getApplicationComponent();
}
