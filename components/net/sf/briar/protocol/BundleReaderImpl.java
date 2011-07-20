package net.sf.briar.protocol;

import java.io.IOException;
import java.security.GeneralSecurityException;

import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BundleReader;
import net.sf.briar.api.protocol.Header;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.serial.FormatException;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

class BundleReaderImpl implements BundleReader {

	private static enum State { START, BATCHES, END };

	private final Reader reader;
	private final ObjectReader<Header> headerReader;
	private final ObjectReader<Batch> batchReader;
	private State state = State.START;

	BundleReaderImpl(Reader reader, ObjectReader<Header> headerReader,
			ObjectReader<Batch> batchReader) {
		this.reader = reader;
		this.headerReader = headerReader;
		this.batchReader = batchReader;
	}

	public Header getHeader() throws IOException, GeneralSecurityException {
		if(state != State.START) throw new IllegalStateException();
		reader.readUserDefinedTag(Tags.HEADER);
		reader.addObjectReader(Tags.HEADER, headerReader);
		Header h = reader.readUserDefinedObject(Tags.HEADER, Header.class);
		reader.removeObjectReader(Tags.HEADER);
		// Expect a list of batches
		reader.readListStart();
		reader.addObjectReader(Tags.BATCH, batchReader);
		state = State.BATCHES;
		return h;
	}

	public Batch getNextBatch() throws IOException, GeneralSecurityException {
		if(state != State.BATCHES) throw new IllegalStateException();
		if(reader.hasListEnd()) {
			reader.removeObjectReader(Tags.BATCH);
			reader.readListEnd();
			// That should be all
			if(!reader.eof()) throw new FormatException();
			state = State.END;
			return null;
		}
		reader.readUserDefinedTag(Tags.BATCH);
		return reader.readUserDefinedObject(Tags.BATCH, Batch.class);
	}

	public void finish() throws IOException {
		reader.close();
	}
}
