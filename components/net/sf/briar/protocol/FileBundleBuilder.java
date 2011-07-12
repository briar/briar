package net.sf.briar.protocol;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import net.sf.briar.api.protocol.BatchBuilder;
import net.sf.briar.api.protocol.Bundle;
import net.sf.briar.api.protocol.HeaderBuilder;
import net.sf.briar.api.protocol.MessageParser;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.WriterFactory;

import com.google.inject.Provider;

public class FileBundleBuilder extends BundleWriter {

	private final File file;
	private final ReaderFactory readerFactory;
	private final MessageParser messageParser;
	private final Provider<HeaderBuilder> headerBuilderProvider;
	private final Provider<BatchBuilder> batchBuilderProvider;

	FileBundleBuilder(File file, long capacity, WriterFactory writerFactory,
			ReaderFactory readerFactory, MessageParser messageParser,
			Provider<HeaderBuilder> headerBuilderProvider,
			Provider<BatchBuilder> batchBuilderProvider) throws IOException {
		super(writerFactory.createWriter(new FileOutputStream(file)), capacity);
		this.file = file;
		this.readerFactory = readerFactory;
		this.messageParser = messageParser;
		this.headerBuilderProvider = headerBuilderProvider;
		this.batchBuilderProvider = batchBuilderProvider;
	}

	public Bundle build() throws IOException {
		super.close();
		return new FileBundle(file, readerFactory, messageParser,
				headerBuilderProvider, batchBuilderProvider);
	}
}
