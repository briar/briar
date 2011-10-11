package net.sf.briar.api;

import java.io.IOException;

import net.sf.briar.api.serial.Writable;
import net.sf.briar.api.serial.Writer;

/**
 * Type-safe wrapper for an integer that uniquely identifies a transport plugin.
 */
public class TransportId implements Writable {

	public static final int MIN_ID = 0;
	public static final int MAX_ID = 65535;

	private final int id;

	public TransportId(int id) {
		if(id < MIN_ID || id > MAX_ID) throw new IllegalArgumentException();
		this.id = id;
	}

	public int getInt() {
		return id;
	}

	public void writeTo(Writer w) throws IOException {
		w.writeInt32(id);
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof TransportId) return id == ((TransportId) o).id;
		return false;
	}

	@Override
	public int hashCode() {
		return id;
	}
}
