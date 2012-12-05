package net.sf.briar.api.protocol;

import java.util.Map;
import java.util.TreeMap;

public class Transport extends TreeMap<String, String> {

	private static final long serialVersionUID = 4900420175715429560L;

	private final TransportId id;

	public Transport(TransportId id, Map<String, String> p) {
		super(p);
		this.id = id;
	}

	public Transport(TransportId id) {
		super();
		this.id = id;
	}

	public TransportId getId() {
		return id;
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof Transport) {
			Transport t = (Transport) o;
			return id.equals(t.id) && super.equals(o);
		}
		return false;
	}
}
