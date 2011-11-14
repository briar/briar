package net.sf.briar.api.protocol;

import java.util.Map;
import java.util.TreeMap;

public class Transport extends TreeMap<String, String> {

	private static final long serialVersionUID = 4900420175715429560L;

	private final TransportId id;
	private final TransportIndex index;

	public Transport(TransportId id, TransportIndex index,
			Map<String, String> p) {
		super(p);
		this.id = id;
		this.index = index;
	}

	public Transport(TransportId id, TransportIndex index) {
		super();
		this.id = id;
		this.index = index;
	}

	public TransportId getId() {
		return id;
	}

	public TransportIndex getIndex() {
		return index;
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof Transport) {
			Transport t = (Transport) o;
			return id.equals(t.id) && index.equals(t.index) && super.equals(o);
		}
		return false;
	}
}
