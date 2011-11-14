package net.sf.briar.protocol.writers;

import net.sf.briar.api.protocol.writers.AuthorWriter;
import net.sf.briar.api.protocol.writers.GroupWriter;
import net.sf.briar.api.protocol.writers.ProtocolWriterFactory;

import com.google.inject.AbstractModule;

public class ProtocolWritersModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(AuthorWriter.class).to(AuthorWriterImpl.class);
		bind(GroupWriter.class).to(GroupWriterImpl.class);
		bind(ProtocolWriterFactory.class).to(ProtocolWriterFactoryImpl.class);
	}
}
