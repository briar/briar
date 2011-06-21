package net.sf.briar.protocol;

import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.Message;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class ProtocolModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(Message.class).to(MessageImpl.class);
	}

	@Provides
	Batch createBatch() {
		return new BatchImpl();
	}
}
