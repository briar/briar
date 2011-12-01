package net.sf.briar.plugins.bluetooth;

import java.util.Collection;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;

class InvitationListener extends AbstractListener {

	private static final Logger LOG =
		Logger.getLogger(InvitationListener.class.getName());

	private final String uuid;

	private volatile String url = null;

	InvitationListener(DiscoveryAgent discoveryAgent, String uuid) {
		super(discoveryAgent);
		this.uuid = uuid;
	}

	String waitForUrl() {
		try {
			finished.await();
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
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

	public void servicesDiscovered(int transaction, ServiceRecord[] services) {
		for(ServiceRecord record : services) {
			// Does this service have a URL?
			String serviceUrl = record.getConnectionURL(
					ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
			if(serviceUrl == null) continue;
			// Does this service have the UUID we're looking for?
			Collection<String> uuids = new TreeSet<String>();
			findNestedClassIds(record.getAttributeValue(0x1), uuids);
			for(String u : uuids) {
				if(uuid.equalsIgnoreCase(u)) {
					// The UUID matches - store the URL
					url = serviceUrl;
					finished.countDown();
					return;
				}
			}
		}
	}
}
