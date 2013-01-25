package net.sf.briar.protocol;

import static net.sf.briar.api.protocol.Types.SUBSCRIPTION_ACK;

import java.io.IOException;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.SubscriptionAck;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.StructReader;

class SubscriptionAckReader implements StructReader<SubscriptionAck> {

	public SubscriptionAck readStruct(Reader r) throws IOException {
		r.readStructId(SUBSCRIPTION_ACK);
		long version = r.readInt64();
		if(version < 0L) throw new FormatException();
		return new SubscriptionAck(version);
	}
}
