package org.briarproject.briar.api.android;

/**
 * Manages mappings between object references and serialisable handles. This
 * enables references to be passed between Android UI objects that belong to
 * the same process but can only communicate via serialisation.
 */
public interface ReferenceManager {

	/**
	 * Returns the object with the given handle, or null if no mapping exists
	 * for the handle.
	 */
	<T> T getReference(long handle, Class<T> c);

	/**
	 * Creates a mapping between the given reference and a handle, and returns
	 * the handle.
	 */
	<T> long putReference(T reference, Class<T> c);

	/**
	 * Removes and returns the object with the given handle, or returns null
	 * if no mapping exists for the handle.
	 */
	<T> T removeReference(long handle, Class<T> c);
}
