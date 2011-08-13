package net.sf.briar.api.protocol;

import java.io.IOException;
import java.util.Arrays;

import net.sf.briar.api.serial.Writer;

/** Type-safe wrapper for a byte array that uniquely identifies an offer. */
public class OfferId extends UniqueId {

	public OfferId(byte[] id) {
		super(id);
	}

	public void writeTo(Writer w) throws IOException {
		w.writeUserDefinedTag(Tags.OFFER_ID);
		w.writeBytes(id);
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof OfferId)
			return Arrays.equals(id, ((OfferId) o).id);
		return false;
	}
}
