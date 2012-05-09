package net.sf.briar.protocol;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PACKET_LENGTH;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PROPERTIES_PER_TRANSPORT;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PROPERTY_LENGTH;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_TRANSPORTS;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.PacketFactory;
import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.CountingConsumer;
import net.sf.briar.api.serial.StructReader;
import net.sf.briar.api.serial.Reader;

class TransportUpdateReader implements StructReader<TransportUpdate> {

	private final PacketFactory packetFactory;
	private final StructReader<Transport> transportReader;

	TransportUpdateReader(PacketFactory packetFactory) {
		this.packetFactory = packetFactory;
		transportReader = new TransportReader();
	}

	public TransportUpdate readStruct(Reader r) throws IOException {
		// Initialise the consumer
		Consumer counting = new CountingConsumer(MAX_PACKET_LENGTH);
		// Read the data
		r.addConsumer(counting);
		r.readStructId(Types.TRANSPORT_UPDATE);
		r.addStructReader(Types.TRANSPORT, transportReader);
		Collection<Transport> transports = r.readList(Transport.class);
		r.removeStructReader(Types.TRANSPORT);
		if(transports.size() > MAX_TRANSPORTS) throw new FormatException();
		long timestamp = r.readInt64();
		r.removeConsumer(counting);
		// Check for duplicate IDs or indices
		Set<TransportId> ids = new HashSet<TransportId>();
		Set<TransportIndex> indices = new HashSet<TransportIndex>();
		for(Transport t : transports) {
			if(!ids.add(t.getId())) throw new FormatException();
			if(!indices.add(t.getIndex())) throw new FormatException();
		}
		// Build and return the transport update
		return packetFactory.createTransportUpdate(transports, timestamp);
	}

	private static class TransportReader implements StructReader<Transport> {

		public Transport readStruct(Reader r) throws IOException {
			r.readStructId(Types.TRANSPORT);
			// Read the ID
			byte[] b = r.readBytes(UniqueId.LENGTH);
			if(b.length != UniqueId.LENGTH) throw new FormatException();
			TransportId id = new TransportId(b);
			// Read the index
			int i = r.readInt32();
			if(i < 0 || i >= MAX_TRANSPORTS) throw new FormatException();
			TransportIndex index = new TransportIndex(i);
			// Read the properties
			r.setMaxStringLength(MAX_PROPERTY_LENGTH);
			Map<String, String> m = r.readMap(String.class, String.class);
			r.resetMaxStringLength();
			if(m.size() > MAX_PROPERTIES_PER_TRANSPORT)
				throw new FormatException();
			return new Transport(id, index, m);
		}
	}
}
