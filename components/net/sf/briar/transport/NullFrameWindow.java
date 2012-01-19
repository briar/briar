package net.sf.briar.transport;

class NullFrameWindow implements FrameWindow {

	private long centre = 0L;

	public boolean contains(long frameNumber) {
		return frameNumber == centre;
	}

	public boolean advance(long frameNumber) {
		return frameNumber == centre;
	}

	public boolean remove(long frameNumber) {
		if(frameNumber != centre) return false;
		centre++;
		return true;
	}
}
