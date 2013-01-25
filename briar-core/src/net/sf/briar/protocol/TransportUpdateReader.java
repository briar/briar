package net.sf.briar.protocol;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PACKET_LENGTH;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PROPERTIES_PER_TRANSPORT;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PROPERTY_LENGTH;
import static net.sf.briar.api.protocol.Types.TRANSPORT_UPDATE;

import java.io.IOException;
import java.util.Map;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.CountingConsumer;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.StructReader;

class TransportUpdateReader implements StructReader<TransportUpdate> {

	public TransportUpdate readStruct(Reader r) throws IOException {
		Consumer counting = new CountingConsumer(MAX_PACKET_LENGTH);
		r.addConsumer(counting);
		r.readStructId(TRANSPORT_UPDATE);
		// Read the transport ID
		byte[] b = r.readBytes(UniqueId.LENGTH);
		if(b.length < UniqueId.LENGTH) throw new FormatException();
		TransportId id = new TransportId(b);
		// Read the transport properties
		r.setMaxStringLength(MAX_PROPERTY_LENGTH);
		Map<String, String> m = r.readMap(String.class, String.class);
		r.resetMaxStringLength();
		if(m.size() > MAX_PROPERTIES_PER_TRANSPORT)
			throw new FormatException();
		// Read the version number
		long version = r.readInt64();
		if(version < 0L) throw new FormatException();
		r.removeConsumer(counting);
		// Build and return the transport update
		return new TransportUpdate(id, new TransportProperties(m), version);
	}
}
