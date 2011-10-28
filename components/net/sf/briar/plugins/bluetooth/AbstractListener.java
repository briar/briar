package net.sf.briar.plugins.bluetooth;

import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicInteger;

import javax.bluetooth.DataElement;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.UUID;

abstract class AbstractListener implements DiscoveryListener {

	protected final DiscoveryAgent discoveryAgent;
	protected final AtomicInteger searches = new AtomicInteger(1);

	protected boolean finished = false; // Locking: this

	protected AbstractListener(DiscoveryAgent discoveryAgent) {
		this.discoveryAgent = discoveryAgent;
	}

	public void inquiryCompleted(int discoveryType) {
		if(searches.decrementAndGet() == 0) {
			synchronized(this) {
				finished = true;
				notifyAll();
			}
		}
	}

	public void serviceSearchCompleted(int transaction, int response) {
		if(searches.decrementAndGet() == 0) {
			synchronized(this) {
				finished = true;
				notifyAll();
			}
		}
	}

	protected Object getDataElementValue(Object o) {
		if(o instanceof DataElement) {
			// Bluecove throws an exception if the type is unknown
			try {
				return ((DataElement) o).getValue();
			} catch(ClassCastException e) {
				return null;
			}
		}
		return null;
	}

	protected void findNestedClassIds(Object o, Collection<String> ids) {
		o = getDataElementValue(o);
		if(o instanceof Enumeration) {
			for(Object o1 : Collections.list((Enumeration<?>) o)) {
				findNestedClassIds(o1, ids);
			}
		} else if(o instanceof UUID) {
			ids.add(o.toString());
		}
	}
}
