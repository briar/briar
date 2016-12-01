package org.briarproject.bramble.plugin.bluetooth;

import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DataElement;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;

import static java.util.logging.Level.WARNING;

class InvitationListener implements DiscoveryListener {

	private static final Logger LOG =
			Logger.getLogger(InvitationListener.class.getName());

	private final AtomicInteger searches = new AtomicInteger(1);
	private final CountDownLatch finished = new CountDownLatch(1);
	private final DiscoveryAgent discoveryAgent;
	private final String uuid;

	private volatile String url = null;

	InvitationListener(DiscoveryAgent discoveryAgent, String uuid) {
		this.discoveryAgent = discoveryAgent;
		this.uuid = uuid;
	}

	String waitForUrl() throws InterruptedException {
		finished.await();
		return url;
	}

	@Override
	public void deviceDiscovered(RemoteDevice device, DeviceClass deviceClass) {
		UUID[] uuids = new UUID[] {new UUID(uuid, false)};
		// Try to discover the services associated with the UUID
		try {
			discoveryAgent.searchServices(null, uuids, device, this);
			searches.incrementAndGet();
		} catch (BluetoothStateException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	@Override
	public void servicesDiscovered(int transaction, ServiceRecord[] services) {
		for (ServiceRecord record : services) {
			// Does this service have a URL?
			String serviceUrl = record.getConnectionURL(
					ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
			if (serviceUrl == null) continue;
			// Does this service have the UUID we're looking for?
			Collection<String> uuids = new TreeSet<>();
			findNestedClassIds(record.getAttributeValue(0x1), uuids);
			for (String u : uuids) {
				if (uuid.equalsIgnoreCase(u)) {
					// The UUID matches - store the URL
					url = serviceUrl;
					finished.countDown();
					return;
				}
			}
		}
	}

	@Override
	public void inquiryCompleted(int discoveryType) {
		if (searches.decrementAndGet() == 0) finished.countDown();
	}

	@Override
	public void serviceSearchCompleted(int transaction, int response) {
		if (searches.decrementAndGet() == 0) finished.countDown();
	}

	// UUIDs are sometimes buried in nested data elements
	private void findNestedClassIds(Object o, Collection<String> ids) {
		o = getDataElementValue(o);
		if (o instanceof Enumeration<?>) {
			for (Object o1 : Collections.list((Enumeration<?>) o))
				findNestedClassIds(o1, ids);
		} else if (o instanceof UUID) {
			ids.add(o.toString());
		}
	}

	private Object getDataElementValue(Object o) {
		if (o instanceof DataElement) {
			// Bluecove throws an exception if the type is unknown
			try {
				return ((DataElement) o).getValue();
			} catch (ClassCastException e) {
				return null;
			}
		}
		return null;
	}
}
