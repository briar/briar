package org.briarproject.reliability;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.briarproject.api.reliability.WriteHandler;
import org.briarproject.api.system.Clock;

class Sender {

	// All times are in milliseconds
	private static final int WRITE_TIMEOUT = 5 * 60 * 1000;
	private static final int MIN_RTO = 1000;
	private static final int MAX_RTO = 60 * 1000;
	private static final int INITIAL_RTT = 0;
	private static final int INITIAL_RTT_VAR = 3 * 1000;
	private static final int MAX_WINDOW_SIZE = 64 * Data.MAX_PAYLOAD_LENGTH;

	private final Clock clock;
	private final WriteHandler writeHandler;
	private final LinkedList<Outstanding> outstanding;

	private int outstandingBytes = 0;
	private int windowSize = Data.MAX_PAYLOAD_LENGTH;
	private int rtt = INITIAL_RTT, rttVar = INITIAL_RTT_VAR;
	private int rto = rtt + (rttVar << 2);
	private long lastWindowUpdateOrProbe = Long.MAX_VALUE;
	private boolean dataWaiting = false;

	private Lock synchLock = new ReentrantLock();
	private Condition sendWindowAvailable = synchLock.newCondition();

	Sender(Clock clock, WriteHandler writeHandler) {
		this.clock = clock;
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
		long now = clock.currentTimeMillis();
		Outstanding fastRetransmit = null;
		synchLock.lock();
		try {
			// Remove the acked data frame if it's outstanding
			int foundIndex = -1;
			Iterator<Outstanding> it = outstanding.iterator();
			for(int i = 0; it.hasNext(); i++) {
				Outstanding o = it.next();
				if(o.data.getSequenceNumber() == sequenceNumber) {
					it.remove();
					outstandingBytes -= o.data.getPayloadLength();
					foundIndex = i;
					// Update the round-trip time and retransmission timeout
					if(!o.retransmitted) {
						int sample = (int) (now - o.lastTransmitted);
						int error = sample - rtt;
						rtt += (error >> 3);
						rttVar += (Math.abs(error) - rttVar) >> 2;
						rto = rtt + (rttVar << 2);
						if(rto < MIN_RTO) rto = MIN_RTO;
						else if(rto > MAX_RTO) rto = MAX_RTO;
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
			windowSize = Math.min(a.getWindowSize(), MAX_WINDOW_SIZE);
			// If space has become available, notify any waiting writers
			if(windowSize > oldWindowSize || foundIndex != -1) sendWindowAvailable.signalAll();
		}
		finally{
			synchLock.unlock();
		}
		// Fast retransmission
		if(fastRetransmit != null)
			writeHandler.handleWrite(fastRetransmit.data.getBuffer());
	}

	void tick() throws IOException {
		long now = clock.currentTimeMillis();
		List<Outstanding> retransmit = null;
		boolean sendProbe = false;
		synchLock.lock();
		try {
			if(outstanding.isEmpty()) {
				if(dataWaiting && now - lastWindowUpdateOrProbe > rto) {
					sendProbe = true;
					rto <<= 1;
					if(rto > MAX_RTO) rto = MAX_RTO;
				}
			} else {
				Iterator<Outstanding> it = outstanding.iterator();
				while(it.hasNext()) {
					Outstanding o = it.next();
					if(now - o.lastTransmitted > rto) {
						it.remove();
						if(retransmit == null)
							retransmit = new ArrayList<Outstanding>();
						retransmit.add(o);
						// Update the retransmission timeout
						rto <<= 1;
						if(rto > MAX_RTO) rto = MAX_RTO;
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
		finally{
			synchLock.unlock();
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
		synchLock.lock();
		try {
			// Wait for space in the window
			long now = clock.currentTimeMillis(), end = now + WRITE_TIMEOUT;
			while(now < end && outstandingBytes + payloadLength >= windowSize) {
				dataWaiting = true;
				sendWindowAvailable.await(end - now, TimeUnit.MILLISECONDS);
				now = clock.currentTimeMillis();
			}
			if(outstandingBytes + payloadLength >= windowSize)
				throw new IOException("Write timed out");
			outstanding.add(new Outstanding(d, now));
			outstandingBytes += payloadLength;
			dataWaiting = false;
		}
		finally{
			synchLock.unlock();
		}
		writeHandler.handleWrite(d.getBuffer());
	}

	void flush() throws IOException, InterruptedException {
		synchLock.lock();
		try{
			while(dataWaiting || !outstanding.isEmpty()) sendWindowAvailable.await();
		}
		finally{
			synchLock.unlock();
		}
	}

	private static class Outstanding {

		private final Data data;

		private volatile long lastTransmitted;
		private volatile boolean retransmitted;

		private Outstanding(Data data, long lastTransmitted) {
			this.data = data;
			this.lastTransmitted = lastTransmitted;
			retransmitted = false;
		}
	}
}
