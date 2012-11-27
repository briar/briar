package net.sf.briar.plugins.modem;

import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

class ReliabilityLayer implements ReadHandler, WriteHandler {

	private static final Logger LOG =
			Logger.getLogger(ReliabilityLayer.class.getName());

	private final WriteHandler writeHandler;
	private final Receiver receiver;
	private final SlipDecoder decoder;
	private final ReceiverInputStream inputStream;
	private final SenderOutputStream outputStream;
	private final BlockingQueue<byte[]> writes;

	private volatile boolean valid = true;

	ReliabilityLayer(WriteHandler writeHandler) {
		this.writeHandler = writeHandler;
		SlipEncoder encoder = new SlipEncoder(this);
		Sender sender = new Sender(encoder);
		receiver = new Receiver(sender);
		decoder = new SlipDecoder(receiver);
		inputStream = new ReceiverInputStream(receiver);
		outputStream = new SenderOutputStream(sender);
		writes = new LinkedBlockingQueue<byte[]>();
	}

	void init() {
		new Thread("ReliabilityLayer") {
			@Override
			public void run() {
				try {
					while(valid) {
						byte[] b = writes.take();
						if(b.length == 0) return; // Poison pill
						writeHandler.handleWrite(b, b.length);
					}
				} catch(InterruptedException e) {
					if(LOG.isLoggable(WARNING))
						LOG.warning("Interrupted while writing");
					valid = false;
					Thread.currentThread().interrupt();
				} catch(IOException e) {
					if(LOG.isLoggable(WARNING))
						LOG.warning("Interrupted while writing");
					valid = false;
				}
			}
		}.start();
	}

	InputStream getInputStream() {
		return inputStream;
	}

	OutputStream getOutputStream() {
		return outputStream;
	}

	void invalidate() {
		valid = false;
		receiver.invalidate();
		writes.add(new byte[0]); // Poison pill
	}

	// The modem calls this method to pass data up to the SLIP decoder
	public void handleRead(byte[] b, int length) throws IOException {
		if(!valid) throw new IOException("Connection closed");
		decoder.handleRead(b, length);
	}

	// The SLIP encoder calls this method to pass data down to the modem
	public void handleWrite(byte[] b, int length) throws IOException {
		if(!valid) throw new IOException("Connection closed");
		if(length == 0) return;
		if(length < b.length) {
			byte[] copy = new byte[length];
			System.arraycopy(b, 0, copy, 0, length);
			b = copy;
		}
		writes.add(b);
	}
}
