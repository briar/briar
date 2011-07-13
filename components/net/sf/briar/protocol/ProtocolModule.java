package net.sf.briar.protocol;

import net.sf.briar.api.protocol.BundleReader;
import net.sf.briar.api.protocol.BundleWriter;
import net.sf.briar.api.protocol.MessageEncoder;
import net.sf.briar.api.protocol.MessageParser;

import com.google.inject.AbstractModule;

public class ProtocolModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(BatchFactory.class).to(BatchFactoryImpl.class);
		bind(BundleReader.class).to(BundleReaderImpl.class);
		bind(BundleWriter.class).to(BundleWriterImpl.class);
		bind(HeaderFactory.class).to(HeaderFactoryImpl.class);
		bind(MessageEncoder.class).to(MessageEncoderImpl.class);
		bind(MessageParser.class).to(MessageParserImpl.class);
	}
}
