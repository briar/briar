package org.briarproject.bramble.api.sync.tree;

public class TreeNode {

	private final TreeHash hash;
	private final int height, firstBlockNumber, lastBlockNumber;

	TreeNode(TreeHash hash, int height, int firstBlockNumber,
			int lastBlockNumber) {
		this.hash = hash;
		this.height = height;
		this.firstBlockNumber = firstBlockNumber;
		this.lastBlockNumber = lastBlockNumber;
	}

	public TreeHash getHash() {
		return hash;
	}

	public int getHeight() {
		return height;
	}

	public int getFirstBlockNumber() {
		return firstBlockNumber;
	}

	public int getLastBlockNumber() {
		return lastBlockNumber;
	}
}
