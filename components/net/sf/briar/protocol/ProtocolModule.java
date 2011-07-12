package net.sf.briar.protocol;

import net.sf.briar.api.protocol.Message;

import com.google.inject.AbstractModule;

public class ProtocolModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(Message.class).to(MessageImpl.class);
	}
}
