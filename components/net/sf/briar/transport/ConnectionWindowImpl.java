package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.CONNECTION_WINDOW_SIZE;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.util.HashSet;
import java.util.Set;

import net.sf.briar.api.transport.ConnectionWindow;

// This class is not thread-safe
class ConnectionWindowImpl implements ConnectionWindow {

	private final Set<Long> unseen;

	private long centre;

	ConnectionWindowImpl() {
		unseen = new HashSet<Long>();
		for(long l = 0; l < CONNECTION_WINDOW_SIZE / 2; l++) unseen.add(l);
		centre = 0;
	}

	ConnectionWindowImpl(Set<Long> unseen) {
		long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
		for(long l : unseen) {
			if(l < 0 || l > MAX_32_BIT_UNSIGNED)
				throw new IllegalArgumentException();
			if(l < min) min = l;
			if(l > max) max = l;
		}
		if(max - min > CONNECTION_WINDOW_SIZE)
			throw new IllegalArgumentException();
		centre = max - CONNECTION_WINDOW_SIZE / 2 + 1;
		for(long l = centre; l <= max; l++) {
			if(!unseen.contains(l)) throw new IllegalArgumentException();
		}
		this.unseen = unseen;
	}

	public boolean isSeen(long connection) {
		return !unseen.contains(connection);
	}

	public void setSeen(long connection) {
		long bottom = getBottom(centre);
		long top = getTop(centre);
		if(connection < bottom || connection > top)
			throw new IllegalArgumentException();
		if(!unseen.remove(connection))
			throw new IllegalArgumentException();
		if(connection >= centre) {
			centre = connection + 1;
			long newBottom = getBottom(centre);
			long newTop = getTop(centre);
			for(long l = bottom; l < newBottom; l++) unseen.remove(l);
			for(long l = top + 1; l <= newTop; l++) unseen.add(l);
		}
	}

	// Returns the lowest value contained in a window with the given centre
	private static long getBottom(long centre) {
		return Math.max(0, centre - CONNECTION_WINDOW_SIZE / 2);
	}

	// Returns the highest value contained in a window with the given centre
	private static long getTop(long centre) {
		return Math.min(MAX_32_BIT_UNSIGNED,
				centre + CONNECTION_WINDOW_SIZE / 2 - 1);
	}

	public Set<Long> getUnseen() {
		return unseen;
	}
}
