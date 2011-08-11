package net.sf.briar.transport;

import java.util.ArrayList;
import java.util.Collection;

import net.sf.briar.api.transport.ConnectionWindow;

class ConnectionWindowImpl implements ConnectionWindow {

	private static final long MAX_32_BIT_UNSIGNED = 4294967295L; // 2^32 - 1

	private long centre;
	private int bitmap;

	ConnectionWindowImpl(long centre, int bitmap) {
		this.centre = centre;
		this.bitmap = bitmap;
	}

	public long getCentre() {
		return centre;
	}

	public int getBitmap() {
		return bitmap;
	}

	public boolean isSeen(long connectionNumber) {
		int offset = getOffset(connectionNumber);
		int mask = 0x80000000 >>> offset;
		return (bitmap & mask) != 0;
	}

	private int getOffset(long connectionNumber) {
		if(connectionNumber < 0L) throw new IllegalArgumentException();
		if(connectionNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		int offset = (int) (connectionNumber - centre) + 16;
		if(offset < 0 || offset > 31) throw new IllegalArgumentException();
		return offset;
	}

	public void setSeen(long connectionNumber) {
		int offset = getOffset(connectionNumber);
		int mask = 0x80000000 >>> offset;
		if((bitmap & mask) != 0) throw new IllegalArgumentException();
		bitmap |= mask;
		// If the new connection number is above the centre, slide the window
		if(connectionNumber >= centre) {
			centre = connectionNumber + 1;
			bitmap <<= offset - 16 + 1;
		}
	}

	public Collection<Long> getUnseenConnectionNumbers() {
		Collection<Long> unseen = new ArrayList<Long>();
		for(int i = 0; i < 32; i++) {
			int mask = 0x80000000 >>> i;
			if((bitmap & mask) == 0) {
				long c = centre - 16 + i;
				if(c >= 0L && c <= MAX_32_BIT_UNSIGNED) unseen.add(c);
			}
		}
		assert unseen.contains(centre) || centre == MAX_32_BIT_UNSIGNED + 1;
		return unseen;
	}
}
