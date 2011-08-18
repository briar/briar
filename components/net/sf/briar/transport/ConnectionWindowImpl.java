package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_32_BIT_UNSIGNED;

import java.util.ArrayList;
import java.util.Collection;

import net.sf.briar.api.transport.ConnectionWindow;

class ConnectionWindowImpl implements ConnectionWindow {

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

	public boolean isSeen(long connection) {
		int offset = getOffset(connection);
		int mask = 0x80000000 >>> offset;
		return (bitmap & mask) != 0;
	}

	private int getOffset(long connection) {
		if(connection < 0L) throw new IllegalArgumentException();
		if(connection > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		int offset = (int) (connection - centre) + 16;
		if(offset < 0 || offset > 31) throw new IllegalArgumentException();
		return offset;
	}

	public void setSeen(long connection) {
		int offset = getOffset(connection);
		int mask = 0x80000000 >>> offset;
		if((bitmap & mask) != 0) throw new IllegalArgumentException();
		bitmap |= mask;
		// If the new connection number is above the centre, slide the window
		if(connection >= centre) {
			centre = connection + 1;
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
		// The centre of the window should be an unseen value unless the
		// maximum possible value has been seen
		assert unseen.contains(centre) || centre == MAX_32_BIT_UNSIGNED + 1;
		return unseen;
	}
}
