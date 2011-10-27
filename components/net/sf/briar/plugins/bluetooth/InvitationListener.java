package net.sf.briar.plugins.bluetooth;

import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DataElement;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;

class InvitationListener implements DiscoveryListener {

	private static final Logger LOG =
		Logger.getLogger(InvitationListener.class.getName());

	private final AtomicInteger searches = new AtomicInteger(1);
	private final DiscoveryAgent discoveryAgent;
	private final String uuid;

	private String url = null; // Locking: this
	private boolean finished = false; // Locking: this

	InvitationListener(DiscoveryAgent discoveryAgent, String uuid) {
		this.discoveryAgent = discoveryAgent;
		this.uuid = uuid;
	}

	synchronized String waitForUrl() {
		while(!finished) {
			try {
				wait();
			} catch(InterruptedException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
		return url;
	}

	public void deviceDiscovered(RemoteDevice device, DeviceClass deviceClass) {
		UUID[] uuids = new UUID[] { new UUID(uuid, false) };
		// Try to discover the services associated with the UUID
		try {
			discoveryAgent.searchServices(null, uuids, device, this);
			searches.incrementAndGet();
		} catch(BluetoothStateException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
		}
	}

	public void inquiryCompleted(int discoveryType) {
		if(searches.decrementAndGet() == 0) {
			synchronized(this) {
				finished = true;
				notifyAll();
			}
		}
	}

	public void servicesDiscovered(int transaction, ServiceRecord[] services) {
		for(ServiceRecord record : services) {
			// Does this service have a URL?
			String serviceUrl = record.getConnectionURL(
					ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
			if(serviceUrl == null) continue;
			// Does this service have the UUID we're looking for?
			DataElement classIds = record.getAttributeValue(0x1);
			if(classIds == null) continue;
			@SuppressWarnings("unchecked")
			Enumeration<DataElement> e =
				(Enumeration<DataElement>) classIds.getValue();
			for(DataElement classId : Collections.list(e)) {
				UUID serviceUuid = (UUID) classId.getValue();
				if(uuid.equals(serviceUuid.toString())) {
					// The UUID matches - store the URL
					synchronized(this) {
						url = serviceUrl;
						finished = true;
						notifyAll();
					}
					return;
				}
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
}
