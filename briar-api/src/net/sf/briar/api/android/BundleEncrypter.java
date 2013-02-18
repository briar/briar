package net.sf.briar.api.android;

import android.os.Bundle;

/**
 * Encrypts and decrypts the contents of bundles in case the operating system
 * writes them to unencrypted storage.
 * <p>
 * This interface is designed to be accessed from the UI thread, so
 * implementations may not be thread-safe.
 */
public interface BundleEncrypter {

	/**
	 * Encrypts the given bundle, replacing its contents with the encrypted
	 * data.
	 */
	void encrypt(Bundle b);

	/**
	 * Decrypts the given bundle, replacing its contents with the decrypted
	 * data, or returns false if the bundle contains invalid data, which may
	 * occur if the process that created the encrypted bundle was terminated
	 * and replaced by the current process.
	 */
	boolean decrypt(Bundle b);
}
