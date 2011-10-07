package net.sf.briar.plugins.bluetooth;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;

import net.sf.briar.api.ContactId;

class BluetoothListener implements DiscoveryListener {

	private static final int[] ATTRIBUTES = { 0x100 }; // Service name

	private final AtomicInteger searches = new AtomicInteger(1);
	private final DiscoveryAgent discoveryAgent;
	private final Map<String, ContactId> addresses;
	private final Map<ContactId, String> uuids;
	private final Map<ContactId, String> urls;

	BluetoothListener(DiscoveryAgent discoveryAgent,
			Map<String, ContactId> addresses, Map<ContactId, String> uuids) {
		this.discoveryAgent = discoveryAgent;
		this.addresses = addresses;
		this.uuids = uuids;
		urls = Collections.synchronizedMap(new HashMap<ContactId, String>());
	}

	public Map<ContactId, String> getUrls() {
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
			discoveryAgent.searchServices(ATTRIBUTES, uuids, device, this);
			searches.incrementAndGet();
		} catch(BluetoothStateException e) {
			// FIXME: Logging
			e.printStackTrace();
		}
	}

	public void inquiryCompleted(int discoveryType) {
		if(searches.decrementAndGet() == 0) {
			synchronized(this) {
				notifyAll();
			}
		}
	}

	public void servicesDiscovered(int transaction, ServiceRecord[] services) {
		for(ServiceRecord record : services) {
			// Do we recognise the address?
			RemoteDevice device = record.getHostDevice();
			ContactId c = addresses.get(device.getBluetoothAddress());
			if(c == null) continue;
			// Store the URL
			String url = record.getConnectionURL(
					ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
			if(url != null) urls.put(c, url);
		}
	}

	public void serviceSearchCompleted(int transaction, int response) {
		if(searches.decrementAndGet() == 0) {
			synchronized(this) {
				notifyAll();
			}
		}
	}
}
