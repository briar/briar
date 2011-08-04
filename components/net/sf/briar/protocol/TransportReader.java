package net.sf.briar.protocol;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

import com.google.inject.Inject;

class TransportReader implements ObjectReader<TransportUpdate> {

	private final TransportFactory transportFactory;

	@Inject
	TransportReader(TransportFactory transportFactory) {
		this.transportFactory = transportFactory;
	}

	public TransportUpdate readObject(Reader r) throws IOException {
		// Initialise the consumer
		Consumer counting = new CountingConsumer(TransportUpdate.MAX_SIZE);
		// Read the data
		r.addConsumer(counting);
		r.readUserDefinedTag(Tags.TRANSPORTS);
		// Transport maps are always written in delimited form
		Map<String, Map<String, String>> transports =
			new TreeMap<String, Map<String, String>>();
		r.readMapStart();
		while(!r.hasMapEnd()) {
			String name = r.readString(TransportUpdate.MAX_SIZE);
			Map<String, String> properties = new TreeMap<String, String>();
			r.readMapStart();
			while(!r.hasMapEnd()) {
				String key = r.readString(TransportUpdate.MAX_SIZE);
				String value = r.readString(TransportUpdate.MAX_SIZE);
				properties.put(key, value);
			}
			r.readMapEnd();
			transports.put(name, properties);
		}
		r.readMapEnd();
		long timestamp = r.readInt64();
		r.removeConsumer(counting);
		// Build and return the transport update
		return transportFactory.createTransports(transports, timestamp);
	}
}
