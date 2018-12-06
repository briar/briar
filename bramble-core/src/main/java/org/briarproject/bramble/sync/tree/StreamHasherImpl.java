package org.briarproject.bramble.sync.tree;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.io.BlockSink;
import org.briarproject.bramble.api.io.HashingId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.tree.StreamHasher;
import org.briarproject.bramble.api.sync.tree.TreeHash;
import org.briarproject.bramble.api.sync.tree.TreeHasher;
import org.briarproject.bramble.api.sync.tree.TreeNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import javax.inject.Provider;

import static java.util.Arrays.copyOfRange;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_BLOCK_LENGTH;

@Immutable
@NotNullByDefault
class StreamHasherImpl implements StreamHasher {

	private final TreeHasher treeHasher;
	private final Provider<HashTree> hashTreeProvider;

	@Inject
	StreamHasherImpl(TreeHasher treeHasher,
			Provider<HashTree> hashTreeProvider) {
		this.treeHasher = treeHasher;
		this.hashTreeProvider = hashTreeProvider;
	}

	@Override
	public TreeNode hash(InputStream in, BlockSink sink, HashingId h)
			throws IOException, DbException {
		HashTree tree = hashTreeProvider.get();
		byte[] block = new byte[MAX_BLOCK_LENGTH];
		int read;
		for (int blockNumber = 0; (read = read(in, block)) > 0; blockNumber++) {
			byte[] data;
			if (read == block.length) data = block;
			else data = copyOfRange(block, 0, read);
			sink.putBlock(h, blockNumber, data);
			tree.addLeaf(treeHasher.hashBlock(blockNumber, data));
		}
		TreeNode root = tree.getRoot();
		setPaths(sink, h, root, new LinkedList<>());
		return root;
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

	private void setPaths(BlockSink sink, HashingId h, TreeNode node,
			LinkedList<TreeHash> path) throws DbException {
		if (node.getHeight() == 0) {
			// We've reached a leaf - store the path
			sink.setPath(h, node.getFirstBlockNumber(), path);
		} else {
			// Add the right child's hash to the path and traverse the left
			path.addFirst(node.getRightChild().getHash());
			setPaths(sink, h, node.getLeftChild(), path);
			// Add the left child's hash to the path and traverse the right
			path.removeFirst();
			path.addFirst(node.getLeftChild().getHash());
			setPaths(sink, h, node.getRightChild(), path);
		}
	}
}
