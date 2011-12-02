package net.sf.briar.protocol.writers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.writers.SubscriptionUpdateWriter;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class SubscriptionUpdateWriterImpl implements SubscriptionUpdateWriter {

	private final OutputStream out;
	private final Writer w;

	SubscriptionUpdateWriterImpl(OutputStream out,
			WriterFactory writerFactory) {
		this.out = out;
		w = writerFactory.createWriter(out);
	}

	public void writeSubscriptions(Map<Group, Long> subs, long timestamp)
	throws IOException {
		w.writeStructId(Types.SUBSCRIPTION_UPDATE);
		w.writeMapStart();
		for(Entry<Group, Long> e : subs.entrySet()) {
			writeGroup(w, e.getKey());
			w.writeInt64(e.getValue());
		}
		w.writeMapEnd();
		w.writeInt64(timestamp);
		out.flush();
	}

	private void writeGroup(Writer w, Group g) throws IOException {
		w.writeStructId(Types.GROUP);
		w.writeString(g.getName());
		byte[] publicKey = g.getPublicKey();
		if(publicKey == null) w.writeNull();
		else w.writeBytes(publicKey);
	}
}
