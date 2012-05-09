package net.sf.briar.protocol;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PACKET_LENGTH;

import java.io.IOException;
import java.util.BitSet;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.PacketFactory;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.CountingConsumer;
import net.sf.briar.api.serial.StructReader;
import net.sf.briar.api.serial.Reader;

class RequestReader implements StructReader<Request> {

	private final PacketFactory packetFactory;

	RequestReader(PacketFactory packetFactory) {
		this.packetFactory = packetFactory;
	}

	public Request readStruct(Reader r) throws IOException {
		// Initialise the consumer
		Consumer counting = new CountingConsumer(MAX_PACKET_LENGTH);
		// Read the data
		r.addConsumer(counting);
		r.readStructId(Types.REQUEST);
		int padding = r.readUint7();
		if(padding > 7) throw new FormatException();
		byte[] bitmap = r.readBytes(MAX_PACKET_LENGTH);
		r.removeConsumer(counting);
		// Convert the bitmap into a BitSet
		int length = bitmap.length * 8 - padding;
		BitSet b = new BitSet(length);
		for(int i = 0; i < bitmap.length; i++) {
			for(int j = 0; j < 8 && i * 8 + j < length; j++) {
				byte bit = (byte) (128 >> j);
				if((bitmap[i] & bit) != 0) b.set(i * 8 + j);
			}
		}
		return packetFactory.createRequest(b, length);
	}
}
