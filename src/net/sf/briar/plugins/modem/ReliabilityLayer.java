package net.sf.briar.plugins.modem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class ReliabilityLayer implements ReadHandler, WriteHandler {

	// Write side
	private final WriteHandler writeHandler;
	private final SlipEncoder encoder;
	private final Sender sender;
	private final SenderOutputStream outputStream;
	// Read side
	private final SlipDecoder decoder;
	private final Receiver receiver;
	private final ReceiverInputStream inputStream;

	private volatile boolean valid = true;

	ReliabilityLayer(WriteHandler writeHandler) {
		this.writeHandler = writeHandler;
		encoder = new SlipEncoder(this);
		sender = new Sender(encoder);
		outputStream = new SenderOutputStream(sender);
		receiver = new Receiver(sender);
		decoder = new SlipDecoder(receiver);
		inputStream = new ReceiverInputStream(receiver);
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

	public void handleRead(byte[] b, int length) throws IOException {
		if(!valid) throw new IOException("Connection closed");
		decoder.handleRead(b, length);
	}

	public void handleWrite(byte[] b, int length) throws IOException {
		if(!valid) throw new IOException("Connection closed");
		writeHandler.handleWrite(b, length);
	}
}
