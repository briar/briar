package net.sf.briar.db;

import net.sf.briar.api.db.ConnectionWindow;

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

	public void setCentre(long centre) {
		this.centre = centre;
	}

	public int getBitmap() {
		return bitmap;
	}

	public void setBitmap(int bitmap) {
		this.bitmap = bitmap;
	}
}
