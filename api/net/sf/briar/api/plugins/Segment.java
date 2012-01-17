package net.sf.briar.api.plugins;

public interface Segment {

	void clear();

	byte[] getBuffer();

	int getLength();

	long getSegmentNumber();

	void setLength(int length);

	void setSegmentNumber(long segmentNumber);
}
