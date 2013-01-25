package net.sf.briar.protocol;

import static net.sf.briar.api.protocol.Types.TRANSPORT_ACK;

import java.io.IOException;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.TransportAck;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.StructReader;

class TransportAckReader implements StructReader<TransportAck> {

	public TransportAck readStruct(Reader r) throws IOException {
		r.readStructId(TRANSPORT_ACK);
		byte[] b = r.readBytes(UniqueId.LENGTH);
		if(b.length < UniqueId.LENGTH) throw new FormatException();
		long version = r.readInt64();
		if(version < 0L) throw new FormatException();
		return new TransportAck(new TransportId(b), version);
	}
}
