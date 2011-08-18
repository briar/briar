package net.sf.briar.protocol;

import java.io.IOException;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

class MessageIdReader implements ObjectReader<MessageId> {

	public MessageId readObject(Reader r) throws IOException {
		r.readUserDefinedTag(Tags.MESSAGE_ID);
		byte[] b = r.readBytes(UniqueId.LENGTH);
		if(b.length != UniqueId.LENGTH) throw new FormatException();
		return new MessageId(b);
	}
}
