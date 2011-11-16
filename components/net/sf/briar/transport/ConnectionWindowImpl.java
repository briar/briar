package net.sf.briar.transport;

import static net.sf.briar.api.protocol.ProtocolConstants.CONNECTION_WINDOW_SIZE;

import java.util.HashMap;
import java.util.Map;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.transport.ConnectionWindow;
import net.sf.briar.util.ByteUtils;

class ConnectionWindowImpl implements ConnectionWindow {

	private final CryptoComponent crypto;
	private final int index;
	private final Map<Long, byte[]> unseen;

	private long centre;

	ConnectionWindowImpl(CryptoComponent crypto, TransportIndex i,
			byte[] secret) {
		this.crypto = crypto;
		index = i.getInt();
		unseen = new HashMap<Long, byte[]>();
		for(long l = 0; l < CONNECTION_WINDOW_SIZE / 2; l++) {
			secret = crypto.deriveNextSecret(secret, index, l);
			unseen.put(l, secret);
		}
		centre = 0;
	}

	ConnectionWindowImpl(CryptoComponent crypto, TransportIndex i,
			Map<Long, byte[]> unseen) {
		long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
		for(long l : unseen.keySet()) {
			if(l < 0 || l > ByteUtils.MAX_32_BIT_UNSIGNED)
				throw new IllegalArgumentException();
			if(l < min) min = l;
			if(l > max) max = l;
		}
		if(max - min > CONNECTION_WINDOW_SIZE)
			throw new IllegalArgumentException();
		centre = max - CONNECTION_WINDOW_SIZE / 2 + 1;
		for(long l = centre; l <= max; l++) {
			if(!unseen.containsKey(l)) throw new IllegalArgumentException();
		}
		this.crypto = crypto;
		index = i.getInt();
		this.unseen = unseen;
	}

	public boolean isSeen(long connection) {
		return !unseen.containsKey(connection);
	}

	public byte[] setSeen(long connection) {
		long bottom = getBottom(centre);
		long top = getTop(centre);
		if(connection < bottom || connection > top)
			throw new IllegalArgumentException();
		if(!unseen.containsKey(connection))
			throw new IllegalArgumentException();
		if(connection >= centre) {
			centre = connection + 1;
			long newBottom = getBottom(centre);
			long newTop = getTop(centre);
			for(long l = bottom; l < newBottom; l++) {
				byte[] expired = unseen.remove(l);
				if(expired != null) ByteUtils.erase(expired);
			}
			byte[] topSecret = unseen.get(top);
			assert topSecret != null;
			for(long l = top + 1; l <= newTop; l++) {
				topSecret = crypto.deriveNextSecret(topSecret, index, l);
				unseen.put(l, topSecret);
			}
		}
		byte[] seen = unseen.remove(connection);
		assert seen != null;
		return seen;
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

	public Map<Long, byte[]> getUnseen() {
		return unseen;
	}
}
