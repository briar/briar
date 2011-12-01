package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

/**
 * A thread that calls the writeFullFrame() method of a PaddedConnectionWriter
 * at regular intervals. The interval between calls is determined by a target
 * output rate. If the underlying output stream cannot accept data at the
 * target rate, calls will be made as frequently as the output stream allows.
 */
class FrameScheduler extends Thread {

	private final PaddedConnectionWriter writer;
	private final int millisPerFrame;

	FrameScheduler(PaddedConnectionWriter writer, int bytesPerSecond) {
		this.writer = writer;
		millisPerFrame = bytesPerSecond * 1000 / MAX_FRAME_LENGTH;
	}

	@Override
	public void run() {
		long lastCall = System.currentTimeMillis();
		while(true) {
			long now = System.currentTimeMillis();
			long nextCall = lastCall + millisPerFrame;
			if(nextCall > now) {
				try {
					Thread.sleep(nextCall - now);
				} catch(InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			lastCall = System.currentTimeMillis();
			if(!writer.writeFullFrame()) return;
		}
	}
}