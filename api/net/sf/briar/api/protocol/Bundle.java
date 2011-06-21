package net.sf.briar.api.protocol;


public interface Bundle {

	public void seal();
	BundleId getId();
	long getCapacity();
	long getSize();
	Iterable<BatchId> getAcks();
	void addAck(BatchId b);
	Iterable<GroupId> getSubscriptions();
	void addSubscription(GroupId g);
	Iterable<Batch> getBatches();
	void addBatch(Batch b);
}
