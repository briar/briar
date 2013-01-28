package net.sf.briar.protocol;

import static net.sf.briar.api.protocol.Types.EXPIRY_UPDATE;

import java.io.IOException;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.ExpiryUpdate;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.StructReader;

class ExpiryUpdateReader implements StructReader<ExpiryUpdate> {

	public ExpiryUpdate readStruct(Reader r) throws IOException {
		r.readStructId(EXPIRY_UPDATE);
		long expiry = r.readInt64();
		if(expiry < 0L) throw new FormatException();
		long version = r.readInt64();
		if(version < 0L) throw new FormatException();
		return new ExpiryUpdate(expiry, version);
	}
}
