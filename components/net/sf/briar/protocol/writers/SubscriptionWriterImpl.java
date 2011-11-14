package net.sf.briar.protocol.writers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.writers.GroupWriter;
import net.sf.briar.api.protocol.writers.SubscriptionWriter;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class SubscriptionWriterImpl implements SubscriptionWriter {

	private final OutputStream out;
	private final Writer w;
	private final GroupWriter groupWriter;

	SubscriptionWriterImpl(OutputStream out, WriterFactory writerFactory) {
		this.out = out;
		w = writerFactory.createWriter(out);
		groupWriter = new GroupWriterImpl();
	}

	public void writeSubscriptions(Map<Group, Long> subs, long timestamp)
	throws IOException {
		w.writeUserDefinedId(Types.SUBSCRIPTION_UPDATE);
		w.writeMapStart();
		for(Entry<Group, Long> e : subs.entrySet()) {
			groupWriter.writeGroup(w, e.getKey());
			w.writeInt64(e.getValue());
		}
		w.writeMapEnd();
		w.writeInt64(timestamp);
		out.flush();
	}
}
