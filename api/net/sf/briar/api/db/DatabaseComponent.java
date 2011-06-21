package net.sf.briar.api.db;

import java.util.Set;

import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.Bundle;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;

public interface DatabaseComponent {

	static final long MEGABYTES = 1024L * 1024L;
	static final long GIGABYTES = 1024L * MEGABYTES;

	static final long MAX_DB_SIZE = 2L * GIGABYTES;
	static final long MIN_FREE_SPACE = 300L * MEGABYTES;
	static final long CRITICAL_FREE_SPACE = 100L * MEGABYTES;
	static final long MAX_BYTES_BETWEEN_SPACE_CHECKS = 5L * MEGABYTES;
	static final long MAX_MS_BETWEEN_SPACE_CHECKS = 60L * 1000L; // 1 min
	static final long BYTES_PER_SWEEP = 5L * MEGABYTES;
	static final int CLEANER_SLEEP_MS = 1000; // 1 sec
	static final int RETRANSMIT_THRESHOLD = 3;

	void close() throws DbException;

	void addLocallyGeneratedMessage(Message m) throws DbException;

	void addNeighbour(NeighbourId n) throws DbException;

	void generateBundle(NeighbourId n, Bundle b) throws DbException;

	Rating getRating(AuthorId a) throws DbException;

	Set<GroupId> getSubscriptions() throws DbException;

	void receiveBundle(NeighbourId n, Bundle b) throws DbException;

	void setRating(AuthorId a, Rating r) throws DbException;

	void subscribe(GroupId g) throws DbException;

	void unsubscribe(GroupId g) throws DbException;
}
