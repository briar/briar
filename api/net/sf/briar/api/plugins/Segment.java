package net.sf.briar.api.plugins;

public interface Segment {

	void clear();

	byte[] getBuffer();

	int getLength();

	long getTransmissionNumber();

	void setLength(int length);

	void setTransmissionNumber(int transmission);
}
