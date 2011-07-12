package net.sf.briar.protocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import net.sf.briar.api.protocol.BatchBuilder;
import net.sf.briar.api.protocol.HeaderBuilder;
import net.sf.briar.api.protocol.MessageParser;
import net.sf.briar.api.serial.ReaderFactory;

import com.google.inject.Provider;

class FileBundle extends BundleReader {

	private final File file;

	FileBundle(File file, ReaderFactory readerFactory,
			MessageParser messageParser,
			Provider<HeaderBuilder> headerBuilderProvider,
			Provider<BatchBuilder> batchBuilderProvider) throws IOException {
		super(readerFactory.createReader(new FileInputStream(file)),
				messageParser, headerBuilderProvider, batchBuilderProvider);
		this.file = file;
	}

	public long getSize() throws IOException {
		return file.length();
	}
}
