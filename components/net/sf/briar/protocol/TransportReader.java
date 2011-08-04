package net.sf.briar.protocol;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.protocol.Transports;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

import com.google.inject.Inject;

class TransportReader implements ObjectReader<Transports> {

	private final TransportFactory transportFactory;

	@Inject
	TransportReader(TransportFactory transportFactory) {
		this.transportFactory = transportFactory;
	}

	public Transports readObject(Reader r) throws IOException {
		// Initialise the consumer
		Consumer counting = new CountingConsumer(Transports.MAX_SIZE);
		// Read the data
		r.addConsumer(counting);
		r.readUserDefinedTag(Tags.TRANSPORTS);
		// Transport maps are always written in delimited form
		Map<String, Map<String, String>> outer =
			new TreeMap<String, Map<String, String>>();
		r.readMapStart();
		while(!r.hasMapEnd()) {
			String name = r.readString(Transports.MAX_SIZE);
			Map<String, String> inner = new TreeMap<String, String>();
			r.readMapStart();
			while(!r.hasMapEnd()) {
				String key = r.readString(Transports.MAX_SIZE);
				String value = r.readString(Transports.MAX_SIZE);
				inner.put(key, value);
			}
			r.readMapEnd();
			outer.put(name, inner);
		}
		r.readMapEnd();
		long timestamp = r.readInt64();
		r.removeConsumer(counting);
		// Build and return the transports update
		return transportFactory.createTransports(outer, timestamp);
	}
}
