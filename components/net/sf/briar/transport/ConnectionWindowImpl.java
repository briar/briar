package net.sf.briar.transport;

import static net.sf.briar.api.protocol.ProtocolConstants.CONNECTION_WINDOW_SIZE;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import net.sf.briar.api.transport.ConnectionWindow;
import net.sf.briar.util.ByteUtils;

class ConnectionWindowImpl implements ConnectionWindow {

	private final Set<Long> unseen;

	private long centre;

	ConnectionWindowImpl() {
		unseen = new TreeSet<Long>();
		for(long l = 0; l < CONNECTION_WINDOW_SIZE / 2; l++) unseen.add(l);
		centre = 0;
	}

	ConnectionWindowImpl(Collection<Long> unseen) {
		long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
		for(long l : unseen) {
			if(l < 0 || l > ByteUtils.MAX_32_BIT_UNSIGNED)
				throw new IllegalArgumentException();
			if(l < min) min = l;
			if(l > max) max = l;
		}
		if(max - min > CONNECTION_WINDOW_SIZE)
			throw new IllegalArgumentException();
		this.unseen = new TreeSet<Long>(unseen);
		centre = max - CONNECTION_WINDOW_SIZE / 2 + 1;
		for(long l = centre; l <= max; l++) {
			if(!this.unseen.contains(l)) throw new IllegalArgumentException();
		}
	}

	public boolean isSeen(long connection) {
		return !unseen.contains(connection);
	}

	public void setSeen(long connection) {
		long bottom = getBottom(centre);
		long top = getTop(centre);
		if(connection < bottom || connection > top)
			throw new IllegalArgumentException();
		if(!unseen.remove(connection)) throw new IllegalArgumentException();
		if(connection >= centre) {
			centre = connection + 1;
			long newBottom = getBottom(centre);
			long newTop = getTop(centre);
			for(long l = bottom; l < newBottom; l++) unseen.remove(l);
			for(long l = top + 1; l <= newTop; l++) unseen.add(l);
		}
	}

	// Returns the lowest value contained in a window with the given centre
	private long getBottom(long centre) {
		return Math.max(0, centre - CONNECTION_WINDOW_SIZE / 2);
	}

	// Returns the highest value contained in a window with the given centre
	private long getTop(long centre) {
		return Math.min(ByteUtils.MAX_32_BIT_UNSIGNED,
				centre + CONNECTION_WINDOW_SIZE / 2 - 1);
	}

	public Collection<Long> getUnseen() {
		return unseen;
	}
}
