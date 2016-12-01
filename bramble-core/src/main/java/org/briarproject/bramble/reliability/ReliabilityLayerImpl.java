package org.briarproject.bramble.reliability;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.reliability.ReliabilityLayer;
import org.briarproject.bramble.api.reliability.WriteHandler;
import org.briarproject.bramble.api.system.Clock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.WARNING;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class ReliabilityLayerImpl implements ReliabilityLayer, WriteHandler {

	private static final int TICK_INTERVAL = 500; // Milliseconds

	private static final Logger LOG =
			Logger.getLogger(ReliabilityLayerImpl.class.getName());

	private final Executor executor;
	private final Clock clock;
	private final WriteHandler writeHandler;
	private final BlockingQueue<byte[]> writes;

	private volatile Receiver receiver = null;
	private volatile SlipDecoder decoder = null;
	private volatile ReceiverInputStream inputStream = null;
	private volatile SenderOutputStream outputStream = null;
	private volatile boolean running = false;

	ReliabilityLayerImpl(Executor executor, Clock clock,
			WriteHandler writeHandler) {
		this.executor = executor;
		this.clock = clock;
		this.writeHandler = writeHandler;
		writes = new LinkedBlockingQueue<byte[]>();
	}

	@Override
	public void start() {
		SlipEncoder encoder = new SlipEncoder(this);
		final Sender sender = new Sender(clock, encoder);
		receiver = new Receiver(clock, sender);
		decoder = new SlipDecoder(receiver, Data.MAX_LENGTH);
		inputStream = new ReceiverInputStream(receiver);
		outputStream = new SenderOutputStream(sender);
		running = true;
		executor.execute(new Runnable() {
			@Override
			public void run() {
				long now = clock.currentTimeMillis();
				long next = now + TICK_INTERVAL;
				try {
					while (running) {
						byte[] b = null;
						while (now < next && b == null) {
							b = writes.poll(next - now, MILLISECONDS);
							if (!running) return;
							now = clock.currentTimeMillis();
						}
						if (b == null) {
							sender.tick();
							while (next <= now) next += TICK_INTERVAL;
						} else {
							if (b.length == 0) return; // Poison pill
							writeHandler.handleWrite(b);
						}
					}
				} catch (InterruptedException e) {
					LOG.warning("Interrupted while waiting to write");
					Thread.currentThread().interrupt();
					running = false;
				} catch (IOException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					running = false;
				}
			}
		});
	}

	@Override
	public void stop() {
		running = false;
		receiver.invalidate();
		writes.add(new byte[0]); // Poison pill
	}

	@Override
	public InputStream getInputStream() {
		return inputStream;
	}

	@Override
	public OutputStream getOutputStream() {
		return outputStream;
	}

	// The lower layer calls this method to pass data up to the SLIP decoder
	@Override
	public void handleRead(byte[] b) throws IOException {
		if (running) decoder.handleRead(b);
	}

	// The SLIP encoder calls this method to pass data down to the lower layer
	@Override
	public void handleWrite(byte[] b) {
		if (running && b.length > 0) writes.add(b);
	}
}
