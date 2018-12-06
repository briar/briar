package org.briarproject.bramble.sync.tree;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.io.BlockSink;
import org.briarproject.bramble.api.io.HashingId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.tree.LeafNode;
import org.briarproject.bramble.api.sync.tree.StreamHasher;
import org.briarproject.bramble.api.sync.tree.TreeHash;
import org.briarproject.bramble.api.sync.tree.TreeHasher;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import javax.inject.Provider;

import static java.util.Arrays.copyOfRange;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_BLOCK_LENGTH;

@Immutable
@NotNullByDefault
class StreamHasherImpl implements StreamHasher {

	private final TreeHasher treeHasher;
	private final MessageFactory messageFactory;
	private final Provider<HashTree> hashTreeProvider;

	@Inject
	StreamHasherImpl(TreeHasher treeHasher, MessageFactory messageFactory,
			Provider<HashTree> hashTreeProvider) {
		this.treeHasher = treeHasher;
		this.messageFactory = messageFactory;
		this.hashTreeProvider = hashTreeProvider;
	}

	@Override
	public MessageId hash(InputStream in, BlockSink sink, HashingId h,
			GroupId g, long timestamp) throws IOException, DbException {
		HashTree hashTree = hashTreeProvider.get();
		byte[] block = new byte[MAX_BLOCK_LENGTH];
		int read;
		for (int blockNumber = 0; (read = read(in, block)) > 0; blockNumber++) {
			byte[] data;
			if (read == block.length) data = block;
			else data = copyOfRange(block, 0, read);
			sink.putBlock(h, blockNumber, data);
			LeafNode leaf = treeHasher.hashBlock(blockNumber, data);
			hashTree.addLeaf(leaf);
		}
		// TODO: Set paths on block sink
		TreeHash rootHash = hashTree.getRoot().getHash();
		MessageId m = messageFactory.getMessageId(g, timestamp, rootHash);
		sink.setMessageId(h, m);
		return m;
	}

	/**
	 * Reads a block from the given input stream and returns the number of
	 * bytes read, or 0 if no bytes were read before reaching the end of the
	 * stream.
	 */
	private int read(InputStream in, byte[] block) throws IOException {
		int offset = 0;
		while (offset < block.length) {
			int read = in.read(block, offset, block.length - offset);
			if (read == -1) return offset;
			offset += read;
		}
		return offset;
	}
}
