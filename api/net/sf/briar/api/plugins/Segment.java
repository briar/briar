package net.sf.briar.api.plugins;

public interface Segment {

	byte[] getBuffer();

	int getLength();

	long getSegmentNumber();

	void setLength(int length);

	void setSegmentNumber(long segmentNumber);
}
