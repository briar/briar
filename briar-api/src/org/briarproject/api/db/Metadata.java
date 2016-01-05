package org.briarproject.api.db;

import java.util.Hashtable;

public class Metadata extends Hashtable<String, byte[]> {

	/**
	 * Special value to indicate that a key is being removed.
	 */
	public static final byte[] REMOVE = new byte[0];
}
