package net.sf.briar.protocol;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

class TransportReader implements ObjectReader<TransportUpdate> {

	private final TransportFactory transportFactory;
	private final ObjectReader<Transport> propertiesReader;

	TransportReader(TransportFactory transportFactory) {
		this.transportFactory = transportFactory;
		propertiesReader = new PropertiesReader();
	}

	public TransportUpdate readObject(Reader r) throws IOException {
		// Initialise the consumer
		Consumer counting =
			new CountingConsumer(ProtocolConstants.MAX_PACKET_LENGTH);
		// Read the data
		r.addConsumer(counting);
		r.readUserDefinedId(Types.TRANSPORT_UPDATE);
		r.addObjectReader(Types.TRANSPORT_PROPERTIES, propertiesReader);
		r.setMaxStringLength(ProtocolConstants.MAX_PACKET_LENGTH);
		List<Transport> l = r.readList(Transport.class);
		r.resetMaxStringLength();
		r.removeObjectReader(Types.TRANSPORT_PROPERTIES);
		if(l.size() > TransportUpdate.MAX_PLUGINS_PER_UPDATE)
			throw new FormatException();
		Map<TransportId, TransportProperties> transports =
			new TreeMap<TransportId, TransportProperties>();
		for(Transport t : l) {
			if(transports.put(t.id, t.properties) != null)
				throw new FormatException(); // Duplicate transport ID
		}
		long timestamp = r.readInt64();
		r.removeConsumer(counting);
		// Build and return the transport update
		return transportFactory.createTransportUpdate(transports, timestamp);
	}

	private static class Transport {

		private final TransportId id;
		private final TransportProperties properties;

		Transport(TransportId id, TransportProperties properties) {
			this.id = id;
			this.properties = properties;
		}
	}

	private static class PropertiesReader implements ObjectReader<Transport> {

		public Transport readObject(Reader r) throws IOException {
			r.readUserDefinedId(Types.TRANSPORT_PROPERTIES);
			int i = r.readInt32();
			if(i < TransportId.MIN_ID || i > TransportId.MAX_ID)
				throw new FormatException();
			TransportId id = new TransportId(i);
			r.setMaxStringLength(TransportUpdate.MAX_KEY_OR_VALUE_LENGTH);
			Map<String, String> m = r.readMap(String.class, String.class);
			r.resetMaxStringLength();
			if(m.size() > TransportUpdate.MAX_PROPERTIES_PER_PLUGIN)
				throw new FormatException();
			return new Transport(id, new TransportProperties(m));
		}
	}
}
