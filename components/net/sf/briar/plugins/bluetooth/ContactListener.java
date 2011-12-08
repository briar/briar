package net.sf.briar.plugins.bluetooth;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;

import net.sf.briar.api.ContactId;

class ContactListener extends AbstractListener {

	private static final Logger LOG =
		Logger.getLogger(ContactListener.class.getName());

	private final Map<String, ContactId> addresses;
	private final Map<ContactId, String> uuids;
	private final Map<ContactId, String> urls;

	ContactListener(DiscoveryAgent discoveryAgent,
			Map<String, ContactId> addresses, Map<ContactId, String> uuids) {
		super(discoveryAgent);
		this.addresses = addresses;
		this.uuids = uuids;
		urls = Collections.synchronizedMap(new HashMap<ContactId, String>());
	}

	Map<ContactId, String> waitForUrls() throws InterruptedException {
		finished.await();
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

	public void servicesDiscovered(int transaction, ServiceRecord[] services) {
		for(ServiceRecord record : services) {
			// Do we recognise the address?
			RemoteDevice device = record.getHostDevice();
			ContactId c = addresses.get(device.getBluetoothAddress());
			if(c == null) continue;
			// Do we have a UUID for this contact?
			String uuid = uuids.get(c);
			if(uuid == null) return;
			// Does this service have a URL?
			String serviceUrl = record.getConnectionURL(
					ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
			if(serviceUrl == null) continue;
			// Does this service have the UUID we're looking for?
			Collection<String> uuids = new TreeSet<String>();
			findNestedClassIds(record.getAttributeValue(0x1), uuids);
			for(String u : uuids) {
				if(uuid.equalsIgnoreCase(u.toString())) {
					// The UUID matches - store the URL
					urls.put(c, serviceUrl);
				}
			}
		}
	}
}