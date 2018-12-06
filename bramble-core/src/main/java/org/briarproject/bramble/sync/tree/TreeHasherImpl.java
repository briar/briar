package org.briarproject.bramble.sync.tree;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.sync.tree.LeafNode;
import org.briarproject.bramble.api.sync.tree.ParentNode;
import org.briarproject.bramble.api.sync.tree.TreeHash;
import org.briarproject.bramble.api.sync.tree.TreeHasher;
import org.briarproject.bramble.api.sync.tree.TreeNode;

import javax.inject.Inject;

import static org.briarproject.bramble.api.sync.Message.FORMAT_VERSION;
import static org.briarproject.bramble.api.sync.MessageId.BLOCK_LABEL;
import static org.briarproject.bramble.api.sync.MessageId.TREE_LABEL;

class TreeHasherImpl implements TreeHasher {

	private static final byte[] FORMAT_VERSION_BYTES =
			new byte[] {FORMAT_VERSION};

	private final CryptoComponent crypto;

	@Inject
	TreeHasherImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	@Override
	public LeafNode hashBlock(int blockNumber, byte[] data) {
		byte[] hash = crypto.hash(BLOCK_LABEL, FORMAT_VERSION_BYTES, data);
		return new LeafNode(new TreeHash(hash), blockNumber);
	}

	@Override
	public ParentNode mergeTrees(TreeNode left, TreeNode right) {
		byte[] hash = crypto.hash(TREE_LABEL, FORMAT_VERSION_BYTES,
				left.getHash().getBytes(), right.getHash().getBytes());
		return new ParentNode(new TreeHash(hash), left, right);
	}
}
