package net.sf.briar.protocol;

import java.io.IOException;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

class BatchIdReader implements ObjectReader<BatchId> {

	public BatchId readObject(Reader r) throws IOException {
		r.readUserDefinedTag(Tags.BATCH_ID);
		byte[] b = r.readBytes(UniqueId.LENGTH);
		if(b.length != UniqueId.LENGTH) throw new FormatException();
		return new BatchId(b);
	}
}
