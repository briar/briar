package net.sf.briar.reliability;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import net.sf.briar.api.reliability.WriteHandler;

class Sender {

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
		writeHandler.handleWrite(a.getBuffer());
	}

	void handleAck(byte[] b) throws IOException {
		if(b.length != Ack.LENGTH) {
			// Ignore ack frame with invalid length
			return;
		}
		Ack a = new Ack(b);
		if(a.getChecksum() != a.calculateChecksum()) {
			// Ignore ack frame with invalid checksum
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
					}
					break;
				}
			}
			// If any older data frames are outstanding, retransmit the oldest
			if(foundIndex > 0) {
				fastRetransmit = outstanding.poll();
				fastRetransmit.lastTransmitted = now;
				fastRetransmit.retransmitted = true;
				outstanding.add(fastRetransmit);
			}
			// Update the window
			lastWindowUpdateOrProbe = now;
			int oldWindowSize = windowSize;
			// Don't accept an unreasonably large window size
			windowSize = Math.min(a.getWindowSize(), Receiver.MAX_WINDOW_SIZE);
			// If space has become available, notify any waiting writers
			if(windowSize > oldWindowSize || foundIndex != -1) notifyAll();
		}
		// Fast retransmission
		if(fastRetransmit != null)
			writeHandler.handleWrite(fastRetransmit.data.getBuffer());
	}

	void tick() throws IOException {
		long now = System.currentTimeMillis();
		List<Outstanding> retransmit = null;
		boolean sendProbe = false;
		synchronized(this) {
			if(outstanding.isEmpty()) {
				if(dataWaiting && now - lastWindowUpdateOrProbe > timeout) {
					sendProbe = true;
					timeout <<= 1;
					if(timeout > MAX_TIMEOUT) timeout = MAX_TIMEOUT;
				}
			} else {
				Iterator<Outstanding> it = outstanding.iterator();
				while(it.hasNext()) {
					Outstanding o = it.next();
					if(now - o.lastTransmitted > timeout) {
						it.remove();
						if(retransmit == null)
							retransmit = new ArrayList<Outstanding>();
						retransmit.add(o);
						timeout <<= 1;
						if(timeout > MAX_TIMEOUT) timeout = MAX_TIMEOUT;
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
	}

	void write(Data d) throws IOException, InterruptedException {
		int payloadLength = d.getPayloadLength();
		synchronized(this) {
			// Wait for space in the window
			while(outstandingBytes + payloadLength >= windowSize) {
				dataWaiting = true;
				wait();
			}
			outstanding.add(new Outstanding(d));
			outstandingBytes += payloadLength;
			dataWaiting = false;
		}
		writeHandler.handleWrite(d.getBuffer());
	}

	synchronized void flush() throws IOException, InterruptedException {
		while(dataWaiting || !outstanding.isEmpty()) wait();
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
