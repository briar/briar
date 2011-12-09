package net.sf.briar.transport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.transport.ConnectionRegistry;

public class ConnectionRegistryImpl implements ConnectionRegistry {

	// Locking: this
	private final Map<TransportId, Map<ContactId, Integer>> connections;

	ConnectionRegistryImpl() {
		connections = new HashMap<TransportId, Map<ContactId, Integer>>();
	}

	public synchronized void registerConnection(ContactId c, TransportId t) {
		Map<ContactId, Integer> m = connections.get(t);
		if(m == null) {
			m = new HashMap<ContactId, Integer>();
			connections.put(t, m);
		}
		Integer count = m.get(c);
		if(count == null) m.put(c, 1);
		else m.put(c, count + 1);
	}

	public synchronized void unregisterConnection(ContactId c, TransportId t) {
		Map<ContactId, Integer> m = connections.get(t);
		if(m == null) throw new IllegalArgumentException();
		Integer count = m.remove(c);
		if(count == null) throw new IllegalArgumentException();
		if(count == 1) {
			if(m.isEmpty()) connections.remove(t);
		} else {
			m.put(c, count - 1);
		}
	}

	public synchronized Collection<ContactId> getConnectedContacts(
			TransportId t) {
		Map<ContactId, Integer> m = connections.get(t);
		if(m == null) return Collections.emptyList();
		List<ContactId> keys = new ArrayList<ContactId>(m.keySet());
		return Collections.unmodifiableList(keys);
	}
}
