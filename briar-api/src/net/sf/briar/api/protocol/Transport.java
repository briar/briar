package net.sf.briar.api.protocol;

import java.util.Map;
import java.util.TreeMap;

public class Transport {

	private final TransportId id;
	private final TreeMap<String, String> properties;

	public Transport(TransportId id, Map<String, String> p) {
		this.id = id;
		properties = new TreeMap<String, String>(p);
	}

	public Transport(TransportId id) {
		this.id = id;
		properties = new TreeMap<String, String>();
	}

	public TransportId getId() {
		return id;
	}

	public Map<String, String> getProperties() {
		return properties;
	}

	@Override
	public int hashCode() {
		return id.hashCode() ^ properties.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof Transport) {
			Transport t = (Transport) o;
			return id.equals(t.id) && properties.equals(t.properties);
		}
		return false;
	}
}
