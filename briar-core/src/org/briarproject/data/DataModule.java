package org.briarproject.data;

import com.google.inject.AbstractModule;

import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.data.MetadataParser;

public class DataModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(BdfReaderFactory.class).to(BdfReaderFactoryImpl.class);
		bind(BdfWriterFactory.class).to(BdfWriterFactoryImpl.class);
		bind(MetadataParser.class).to(MetadataParserImpl.class);
	}
}
