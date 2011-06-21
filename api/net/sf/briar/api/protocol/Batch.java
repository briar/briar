package net.sf.briar.api.protocol;


public interface Batch {

	public static final long CAPACITY = 1024L * 1024L;

	public void seal();
	BatchId getId();
	long getSize();
	Iterable<Message> getMessages();
	void addMessage(Message m);
}