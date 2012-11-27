package net.sf.briar.plugins.modem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class ReliabilityLayer implements ReadHandler, WriteHandler {

	private final WriteHandler writeHandler;
	private final SlipDecoder decoder;
	private final ReceiverInputStream inputStream;
	private final SenderOutputStream outputStream;

	private volatile boolean valid = true;

	ReliabilityLayer(WriteHandler writeHandler) {
		this.writeHandler = writeHandler;
		SlipEncoder encoder = new SlipEncoder(this);
		Sender sender = new Sender(encoder);
		Receiver receiver = new Receiver(sender);
		decoder = new SlipDecoder(receiver);
		inputStream = new ReceiverInputStream(receiver);
		outputStream = new SenderOutputStream(sender);
	}

	InputStream getInputStream() {
		return inputStream;
	}

	OutputStream getOutputStream() {
		return outputStream;
	}

	void invalidate() {
		valid = false;
	}

	// The modem calls this method to pass data up to the SLIP decoder
	public void handleRead(byte[] b, int length) throws IOException {
		if(!valid) throw new IOException("Connection closed");
		decoder.handleRead(b, length);
	}

	// The SLIP encoder calls this method to pass data down to the modem
	public void handleWrite(byte[] b, int length) throws IOException {
		if(!valid) throw new IOException("Connection closed");
		writeHandler.handleWrite(b, length);
	}
}
