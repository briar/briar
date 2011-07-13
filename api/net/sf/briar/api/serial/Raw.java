package net.sf.briar.api.serial;

/**
 * Generic interface for any object that knows how to serialise itself as a
 * raw byte array.
 */
public interface Raw {

	byte[] getBytes();
}
