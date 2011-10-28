package net.sf.briar.plugins.bluetooth;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
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

import net.sf.briar.api.ContactId;

class ContactListener implements DiscoveryListener {

	private static final Logger LOG =
		Logger.getLogger(ContactListener.class.getName());

	private final AtomicInteger searches = new AtomicInteger(1);
	private final DiscoveryAgent discoveryAgent;
	private final Map<String, ContactId> addresses;
	private final Map<ContactId, String> uuids;
	private final Map<ContactId, String> urls;

	private boolean finished = false; // Locking: this

	ContactListener(DiscoveryAgent discoveryAgent,
			Map<String, ContactId> addresses, Map<ContactId, String> uuids) {
		this.discoveryAgent = discoveryAgent;
		this.addresses = addresses;
		this.uuids = uuids;
		urls = Collections.synchronizedMap(new HashMap<ContactId, String>());
	}

	public synchronized Map<ContactId, String> waitForUrls() {
		while(!finished) {
			try {
				wait();
			} catch(InterruptedException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
		return urls;
	}

	public void deviceDiscovered(RemoteDevice device, DeviceClass deviceClass) {
		// Do we recognise the address?
		ContactId contactId = addresses.get(device.getBluetoothAddress());
		if(contactId == null) return;
		// Do we have a UUID for this contact?
		String uuid = uuids.get(contactId);
		if(uuid == null) return;
		UUID[] uuids = new UUID[] { new UUID(uuid, false) };
		// Try to discover the services associated with the UUID
		try {
			discoveryAgent.searchServices(null, uuids, device, this);
		} catch(BluetoothStateException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
		}
		searches.incrementAndGet();
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
			// Do we recognise the address?
			RemoteDevice device = record.getHostDevice();
			String address = device.getBluetoothAddress();
			ContactId c = addresses.get(address);
			if(c == null) continue;
			// Do we have a UUID for this contact?
			String uuid = uuids.get(c);
			if(uuid == null) return;
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
				if(uuid.equalsIgnoreCase(serviceUuid.toString())) {
					// The UUID matches - store the URL
					if(LOG.isLoggable(Level.FINE))
						LOG.fine("Discovered " + uuid + " at " + address);
					urls.put(c, serviceUrl);
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
