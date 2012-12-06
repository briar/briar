package net.sf.briar.plugins.modem;

import static java.util.logging.Level.INFO;

import java.io.IOException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Logger;

class Receiver implements ReadHandler {

	static final int MAX_WINDOW_SIZE = 8 * Data.MAX_PAYLOAD_LENGTH;

	private static final Logger LOG =
			Logger.getLogger(Receiver.class.getName());

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
				if(LOG.isLoggable(INFO)) LOG.info("Waiting for a data frame");
				wait();
			} else {
				Data d = dataFrames.first();
				if(d.getSequenceNumber() == nextSequenceNumber) {
					if(LOG.isLoggable(INFO))
						LOG.info("Reading #" + d.getSequenceNumber());
					dataFrames.remove(d);
					// Update the window
					windowSize += d.getPayloadLength();
					if(LOG.isLoggable(INFO))
						LOG.info("Window at receiver " + windowSize);
					sender.sendAck(0L, windowSize);
					nextSequenceNumber++;
					return d;
				} else {
					if(LOG.isLoggable(INFO))
						LOG.info("Waiting for #" + nextSequenceNumber);
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
			if(LOG.isLoggable(INFO))
				LOG.info("Ignoring unknown frame type: " + b[0]);
			return;
		}
	}

	private synchronized void handleData(byte[] b) throws IOException {
		if(b.length < Data.MIN_LENGTH || b.length > Data.MAX_LENGTH) {
			if(LOG.isLoggable(INFO))
				LOG.info("Ignoring data frame with invalid length");
			return;
		}
		Data d = new Data(b);
		int payloadLength = d.getPayloadLength();
		if(payloadLength > windowSize) {
			if(LOG.isLoggable(INFO)) LOG.info("No space in the window");
			return;
		}
		if(d.getChecksum() != d.calculateChecksum()) {
			if(LOG.isLoggable(INFO))
				LOG.info("Incorrect checksum on data frame");
			return;
		}
		long sequenceNumber = d.getSequenceNumber();
		if(sequenceNumber == 0L) {
			if(LOG.isLoggable(INFO)) LOG.info("Window probe");
		} else if(sequenceNumber < nextSequenceNumber) {
			if(LOG.isLoggable(INFO)) LOG.info("Duplicate data frame");
		} else if(d.isLastFrame()) {
			finalSequenceNumber = sequenceNumber;
			Iterator<Data> it = dataFrames.iterator();
			while(it.hasNext()) {
				Data d1 = it.next();
				if(d1.getSequenceNumber() >= finalSequenceNumber) {
					if(LOG.isLoggable(INFO))
						LOG.info("Received data frame after FIN");
					it.remove();
				}
			}
			if(LOG.isLoggable(INFO)) LOG.info("Received #" + sequenceNumber);
			if(dataFrames.add(d)) {
				windowSize -= payloadLength;
				if(LOG.isLoggable(INFO))
					LOG.info("Window at receiver " + windowSize);
				notifyAll();
			}
		} else if(sequenceNumber < finalSequenceNumber) {
			if(LOG.isLoggable(INFO)) LOG.info("Received #" + sequenceNumber);
			if(dataFrames.add(d)) {
				windowSize -= payloadLength;
				if(LOG.isLoggable(INFO))
					LOG.info("Window at receiver " + windowSize);
				notifyAll();
			}
		} else {
			if(LOG.isLoggable(INFO)) LOG.info("Received data frame after FIN");
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
