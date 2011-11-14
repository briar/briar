package net.sf.briar.api.protocol;


/**
 * Type-safe wrapper for an integer that uniquely identifies a transport plugin
 * within the scope of a single node.
 */
public class TransportIndex {

	private final int index;

	public TransportIndex(int index) {
		if(index < 0 || index >= ProtocolConstants.MAX_TRANSPORTS)
			throw new IllegalArgumentException();
		this.index = index;
	}

	public int getInt() {
		return index;
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof TransportIndex)
			return index == ((TransportIndex) o).index;
		return false;
	}

	@Override
	public int hashCode() {
		return index;
	}
}
