package net.sf.briar.protocol;

import static net.sf.briar.api.protocol.Types.EXPIRY_ACK;

import java.io.IOException;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.ExpiryAck;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.StructReader;

class ExpiryAckReader implements StructReader<ExpiryAck> {

	public ExpiryAck readStruct(Reader r) throws IOException {
		r.readStructId(EXPIRY_ACK);
		long version = r.readInt64();
		if(version < 0L) throw new FormatException();
		return new ExpiryAck(version);
	}
}
