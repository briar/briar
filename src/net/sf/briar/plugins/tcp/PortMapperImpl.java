package net.sf.briar.plugins.tcp;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;

import org.wetorrent.upnp.GatewayDevice;
import org.wetorrent.upnp.GatewayDiscover;
import org.xml.sax.SAXException;

class PortMapperImpl implements PortMapper {

	private static final Logger LOG =
			Logger.getLogger(PortMapperImpl.class.getName());

	private final CountDownLatch started = new CountDownLatch(1);
	private final Collection<Integer> ports =
			new CopyOnWriteArrayList<Integer>();

	private volatile GatewayDevice gateway = null;

	public void start() {
		GatewayDiscover d = new GatewayDiscover();
		try {
			d.discover();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		} catch(SAXException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		} catch(ParserConfigurationException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		}
		gateway = d.getValidGateway();
		started.countDown();
	}

	public void stop() {
		if(gateway == null) return;
		try {
			for(Integer port: ports) {
				gateway.deletePortMapping(port, "TCP");
				if(LOG.isLoggable(INFO))
					LOG.info("Deleted mapping for port " + port); 
			}
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		} catch(SAXException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		}
	}

	public MappingResult map(int port) {
		try {
			started.await();
		} catch(InterruptedException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			Thread.currentThread().interrupt();
			return null;
		}
		if(gateway == null) return null;
		InetAddress internal = gateway.getLocalAddress();
		if(internal == null) return null;
		boolean succeeded = false;
		InetAddress external = null;
		try {
			succeeded = gateway.addPortMapping(port, port,
					internal.getHostAddress(), "TCP", "TCP");
			String externalString = gateway.getExternalIPAddress();
			if(externalString != null)
				external = InetAddress.getByName(externalString);
			if(LOG.isLoggable(INFO)) {
				if(succeeded) LOG.info("External address " + externalString);
				else LOG.info("Could not create port mapping");
			}
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		} catch(SAXException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		}
		if(succeeded) ports.add(port);
		return new MappingResult(internal, external, port, succeeded);
	}
}
