package net.sf.briar.plugins.modem;

import java.io.IOException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

class Receiver implements ReadHandler {

	static final int MAX_WINDOW_SIZE = 8 * Data.MAX_PAYLOAD_LENGTH;

	private final Sender sender;
	private final SortedSet<Data> dataFrames; // Locking: this

	private int windowSize = MAX_WINDOW_SIZE; // Locking: this
	private long finalSequenceNumber = Long.MAX_VALUE;
	private long nextSequenceNumber = 1L;

	private volatile boolean valid = true;

	Receiver(Sender sender) {
		this.sender = sender;
		dataFrames = new TreeSet<Data>(new SequenceNumberComparator());
	}

	synchronized Data read() throws IOException, InterruptedException {
		while(valid) {
			if(dataFrames.isEmpty()) {
				// Wait for a data frame
				wait();
			} else {
				Data d = dataFrames.first();
				if(d.getSequenceNumber() == nextSequenceNumber) {
					dataFrames.remove(d);
					// Update the window
					windowSize += d.getPayloadLength();
					sender.sendAck(0L, windowSize);
					nextSequenceNumber++;
					return d;
				} else {
					// Wait for the next in-order data frame
					wait();
				}
			}
		}
		throw new IOException("Connection closed");
	}

	void invalidate() {
		valid = false;
		synchronized(this) {
			notifyAll();
		}
	}

	public void handleRead(byte[] b) throws IOException {
		if(!valid) throw new IOException("Connection closed");
		switch(b[0]) {
		case 0:
		case Frame.FIN_FLAG:
			handleData(b);
			break;
		case Frame.ACK_FLAG:
			sender.handleAck(b);
			break;
		default:
			// Ignore unknown frame type
			return;
		}
	}

	private synchronized void handleData(byte[] b) throws IOException {
		if(b.length < Data.MIN_LENGTH || b.length > Data.MAX_LENGTH) {
			// Ignore data frame with invalid length
			return;
		}
		Data d = new Data(b);
		int payloadLength = d.getPayloadLength();
		if(payloadLength > windowSize) return; // No space in the window
		if(d.getChecksum() != d.calculateChecksum()) {
			// Ignore data frame with invalid checksum
			return;
		}
		long sequenceNumber = d.getSequenceNumber();
		if(sequenceNumber == 0L) {
			// Window probe
		} else if(sequenceNumber < nextSequenceNumber) {
			// Duplicate data frame
		} else if(d.isLastFrame()) {
			finalSequenceNumber = sequenceNumber;
			// Remove any data frames with higher sequence numbers
			Iterator<Data> it = dataFrames.iterator();
			while(it.hasNext()) {
				Data d1 = it.next();
				if(d1.getSequenceNumber() >= finalSequenceNumber) it.remove();
			}
			if(dataFrames.add(d)) {
				windowSize -= payloadLength;
				notifyAll();
			}
		} else if(sequenceNumber < finalSequenceNumber) {
			if(dataFrames.add(d)) {
				windowSize -= payloadLength;
				notifyAll();
			}
		}
		// Acknowledge the data frame even if it's a duplicate
		sender.sendAck(sequenceNumber, windowSize);
	}

	private static class SequenceNumberComparator implements Comparator<Data> {

		public int compare(Data d1, Data d2) {
			long s1 = d1.getSequenceNumber(), s2 = d2.getSequenceNumber();
			if(s1 < s2) return -1;
			if(s1 > s2) return 1;
			return 0;
		}
	}
}
