package net.sf.briar.plugins.modem;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

class Sender {

	private static final Logger LOG =
			Logger.getLogger(Sender.class.getName());

	// All times are in milliseconds
	private static final int MIN_TIMEOUT = 1000;
	private static final int MAX_TIMEOUT = 60 * 1000;
	private static final int INITIAL_RTT = 0;
	private static final int INITIAL_RTT_VAR = 3 * 1000;

	private final WriteHandler writeHandler;
	private final LinkedList<Outstanding> outstanding; // Locking: this

	private int outstandingBytes = 0; // Locking: this
	private int windowSize = Data.MAX_PAYLOAD_LENGTH; // Locking: this
	private int rtt = INITIAL_RTT, rttVar = INITIAL_RTT_VAR; // Locking: this
	private int timeout = rtt + (rttVar << 2); // Locking: this
	private long lastWindowUpdateOrProbe = Long.MAX_VALUE; // Locking: this
	private boolean dataWaiting = false; // Locking: this

	Sender(WriteHandler writeHandler) {
		this.writeHandler = writeHandler;
		outstanding = new LinkedList<Outstanding>();
	}

	void sendAck(long sequenceNumber, int windowSize) throws IOException {
		Ack a = new Ack();
		a.setSequenceNumber(sequenceNumber);
		a.setWindowSize(windowSize);
		a.setChecksum(a.calculateChecksum());
		if(sequenceNumber == 0L) {
			if(LOG.isLoggable(FINE)) LOG.fine("Sending window update");
		} else {
			if(LOG.isLoggable(FINE))
				LOG.fine("Acknowledging #" + sequenceNumber);
		}
		writeHandler.handleWrite(a.getBuffer());
	}

	void handleAck(byte[] b) {
		if(b.length != Ack.LENGTH) {
			if(LOG.isLoggable(FINE))
				LOG.fine("Ignoring ack frame with invalid length");
			return;
		}
		Ack a = new Ack(b);
		if(a.getChecksum() != a.calculateChecksum()) {
			if(LOG.isLoggable(FINE))
				LOG.fine("Incorrect checksum on ack frame");
			return;
		}
		long sequenceNumber = a.getSequenceNumber();
		long now = System.currentTimeMillis();
		Outstanding fastRetransmit = null;
		synchronized(this) {
			// Remove the acked data frame if it's outstanding
			int foundIndex = -1;
			Iterator<Outstanding> it = outstanding.iterator();
			for(int i = 0; it.hasNext(); i++) {
				Outstanding o = it.next();
				if(o.data.getSequenceNumber() == sequenceNumber) {
					if(LOG.isLoggable(FINE))
						LOG.fine("#" + sequenceNumber + " acknowledged");
					it.remove();
					outstandingBytes -= o.data.getPayloadLength();
					foundIndex = i;
					// Update the round-trip time and retransmission timer
					if(!o.retransmitted) {
						int sample = (int) (now - o.lastTransmitted);
						int error = sample - rtt;
						rtt += (error >> 3);
						rttVar += (Math.abs(error) - rttVar) >> 2;
						timeout = rtt + (rttVar << 2);
						if(timeout < MIN_TIMEOUT) timeout = MIN_TIMEOUT;
						else if(timeout > MAX_TIMEOUT) timeout = MAX_TIMEOUT;
						if(LOG.isLoggable(FINE))
							LOG.fine("RTT " + rtt + ", timeout " + timeout);
					}
					break;
				}
			}
			// If any older data frames are outstanding, retransmit the oldest
			if(foundIndex > 0) {
				fastRetransmit = outstanding.poll();
				if(LOG.isLoggable(FINE)) {
					LOG.fine("Fast retransmitting #"
							+ fastRetransmit.data.getSequenceNumber());
				}
				fastRetransmit.lastTransmitted = now;
				fastRetransmit.retransmitted = true;
				outstanding.add(fastRetransmit);
			}
			// Update the window
			lastWindowUpdateOrProbe = now;
			int oldWindowSize = windowSize;
			windowSize = a.getWindowSize();
			if(LOG.isLoggable(FINE)) LOG.fine("Window at sender " + windowSize);
			// If space has become available, notify any waiting writers
			if(windowSize > oldWindowSize || foundIndex != -1) notifyAll();
		}
		// Fast retransmission
		if(fastRetransmit != null) {
			Data d = fastRetransmit.data;
			try {
				writeHandler.handleWrite(d.getBuffer());
			} catch(IOException e) {
				// FIXME: Do something more meaningful
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			}
		}
	}

	void tick() {
		long now = System.currentTimeMillis();
		List<Outstanding> retransmit = null;
		boolean sendProbe = false;
		synchronized(this) {
			if(outstanding.isEmpty()) {
				if(dataWaiting && now - lastWindowUpdateOrProbe > timeout) {
					if(LOG.isLoggable(FINE)) LOG.fine("Sending window probe");
					sendProbe = true;
					timeout <<= 1;
					if(timeout > MAX_TIMEOUT) timeout = MAX_TIMEOUT;
					if(LOG.isLoggable(FINE))
						LOG.fine("Increasing timeout to " + timeout);
				}
			} else {
				Iterator<Outstanding> it = outstanding.iterator();
				while(it.hasNext()) {
					Outstanding o = it.next();
					if(now - o.lastTransmitted > timeout) {
						if(LOG.isLoggable(FINE)) {
							LOG.fine("Retransmitting #"
									+ o.data.getSequenceNumber());
						}
						it.remove();
						if(retransmit == null)
							retransmit = new ArrayList<Outstanding>();
						retransmit.add(o);
						timeout <<= 1;
						if(timeout > MAX_TIMEOUT) timeout = MAX_TIMEOUT;
						if(LOG.isLoggable(FINE))
							LOG.fine("Increasing timeout to " + timeout);
					}
				}
				if(retransmit != null) {
					for(Outstanding o : retransmit) {
						o.lastTransmitted = now;
						o.retransmitted = true;
						outstanding.add(o);
					}
				}
			}
		}
		try {
			// Send a window probe if necessary
			if(sendProbe) {
				byte[] buf = new byte[Data.MIN_LENGTH];
				Data probe = new Data(buf);
				probe.setChecksum(probe.calculateChecksum());
				writeHandler.handleWrite(buf);
			}
			// Retransmit any lost data frames
			if(retransmit != null) {
				for(Outstanding o : retransmit)
					writeHandler.handleWrite(o.data.getBuffer());
			}
		} catch(IOException e) {
			// FIXME: Do something more meaningful
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			return;
		}
	}

	void write(Data d) throws IOException, InterruptedException {
		int payloadLength = d.getPayloadLength();
		synchronized(this) {
			while(outstandingBytes + payloadLength >= windowSize) {
				if(LOG.isLoggable(FINE))
					LOG.fine("Waiting for space in the window");
				dataWaiting = true;
				wait();
			}
			outstanding.add(new Outstanding(d));
			outstandingBytes += payloadLength;
			dataWaiting = false;
		}
		if(LOG.isLoggable(FINE))
			LOG.fine("Transmitting #" + d.getSequenceNumber());
		writeHandler.handleWrite(d.getBuffer());
	}

	private static class Outstanding {

		private final Data data;

		private volatile long lastTransmitted;
		private volatile boolean retransmitted;

		private Outstanding(Data data) {
			this.data = data;
			lastTransmitted = System.currentTimeMillis();
			retransmitted = false;
		}
	}
}
