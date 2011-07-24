package net.sf.briar.api.protocol;

import java.io.IOException;
import java.util.Arrays;

import net.sf.briar.api.serial.Writer;

/**
 * Type-safe wrapper for a byte array that uniquely identifies a batch of
 * messages.
 */
public class BatchId extends UniqueId {

	public BatchId(byte[] id) {
		super(id);
	}

	public void writeTo(Writer w) throws IOException {
		w.writeUserDefinedTag(Tags.BATCH_ID);
		w.writeBytes(id);
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof BatchId)
			return Arrays.equals(id, ((BatchId) o).id);
		return false;
	}
}
