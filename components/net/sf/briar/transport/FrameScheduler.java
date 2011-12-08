package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A thread that calls the writeFullFrame() method of a PaddedConnectionWriter
 * at regular intervals. The interval between calls is determined by a target
 * output rate. If the underlying output stream cannot accept data at the
 * target rate, calls will be made as frequently as the output stream allows.
 */
class FrameScheduler extends Thread {

	private static final Logger LOG =
		Logger.getLogger(FrameScheduler.class.getName());

	private final PaddedConnectionWriter writer;
	private final int millisPerFrame;

	FrameScheduler(PaddedConnectionWriter writer, int bytesPerSecond) {
		this.writer = writer;
		millisPerFrame = bytesPerSecond * 1000 / MAX_FRAME_LENGTH;
	}

	@Override
	public void run() {
		long lastCall = System.currentTimeMillis();
		try {
			while(true) {
				long now = System.currentTimeMillis();
				long nextCall = lastCall + millisPerFrame;
				if(nextCall > now) Thread.sleep(nextCall - now);
				lastCall = System.currentTimeMillis();
				if(!writer.writeFullFrame()) return;
			}
		} catch(InterruptedException e) {
			if(LOG.isLoggable(Level.INFO))
				LOG.info("Interrupted while waiting to write frame");
			Thread.currentThread().interrupt();
		}
	}
}