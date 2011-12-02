package net.sf.briar.protocol.writers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.BitSet;

import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.writers.RequestWriter;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class RequestWriterImpl implements RequestWriter {

	private final OutputStream out;
	private final Writer w;

	RequestWriterImpl(OutputStream out, WriterFactory writerFactory) {
		this.out = out;
		w = writerFactory.createWriter(out);
	}

	public void writeRequest(BitSet b, int length)
	throws IOException {
		w.writeStructId(Types.REQUEST);
		// If the number of bits isn't a multiple of 8, round up to a byte
		int bytes = length % 8 == 0 ? length / 8 : length / 8 + 1;
		byte[] bitmap = new byte[bytes];
		// I'm kind of surprised BitSet doesn't have a method for this
		for(int i = 0; i < length; i++) {
			if(b.get(i)) {
				int offset = i / 8;
				byte bit = (byte) (128 >> i % 8);
				bitmap[offset] |= bit;
			}
		}
		w.writeBytes(bitmap);
		out.flush();
	}
}
