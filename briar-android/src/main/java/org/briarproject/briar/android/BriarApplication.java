package org.briarproject.briar.android;

/**
 * This exists so that the Application object will not necessarily be cast
 * directly to the Briar application object.
 */
public interface BriarApplication {

	// This build expires on 31 December 2018
	long EXPIRY_DATE = 1546214400 * 1000L;

	AndroidComponent getApplicationComponent();

}
