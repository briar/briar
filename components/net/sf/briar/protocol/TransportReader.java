package net.sf.briar.protocol;

import java.io.IOException;
import java.util.Map;

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
		Map<String, String> transports = r.readMap(String.class, String.class);
		long timestamp = r.readInt64();
		r.removeConsumer(counting);
		// Build and return the transports update
		return transportFactory.createTransports(transports, timestamp);
	}
}
