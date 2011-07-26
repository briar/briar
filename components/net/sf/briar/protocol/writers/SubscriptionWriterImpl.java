package net.sf.briar.protocol.writers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.protocol.writers.SubscriptionWriter;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class SubscriptionWriterImpl implements SubscriptionWriter {

	private final OutputStream out;
	private final Writer w;

	SubscriptionWriterImpl(OutputStream out, WriterFactory writerFactory) {
		this.out = out;
		w = writerFactory.createWriter(out);
	}

	public void writeSubscriptions(Collection<Group> subs) throws IOException {
		w.writeUserDefinedTag(Tags.SUBSCRIPTIONS);
		w.writeList(subs);
		w.writeInt64(System.currentTimeMillis());
		out.flush();
	}
}
