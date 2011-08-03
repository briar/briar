package net.sf.briar.protocol;

import java.io.IOException;
import java.util.BitSet;

import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

import com.google.inject.Inject;

class RequestReader implements ObjectReader<Request> {

	private final RequestFactory requestFactory;

	@Inject
	RequestReader(RequestFactory requestFactory) {
		this.requestFactory = requestFactory;
	}

	public Request readObject(Reader r) throws IOException {
		// Initialise the consumer
		Consumer counting = new CountingConsumer(Request.MAX_SIZE);
		// Read the data
		r.addConsumer(counting);
		r.readUserDefinedTag(Tags.REQUEST);
		byte[] bitmap = r.readBytes(Request.MAX_SIZE);
		r.removeConsumer(counting);
		// Convert the bitmap into a BitSet
		BitSet b = new BitSet(bitmap.length * 8);
		for(int i = 0; i < bitmap.length; i++) {
			for(int j = 0; j < 8; j++) {
				byte bit = (byte) (128 >> j);
				if((bitmap[i] & bit) != 0) b.set(i * 8 + j);
			}
		}
		return requestFactory.createRequest(b);
	}
}
