package org.briarproject.bramble.api.rendezvous;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

/**
 * A source of key material for use in making rendezvous connections.
 */
@NotNullByDefault
public interface KeyMaterialSource {

	/**
	 * Returns the requested amount of key material.
	 */
	byte[] getKeyMaterial(int length);
}
