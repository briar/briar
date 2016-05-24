package org.briarproject.clients;

import org.briarproject.TestUtils;
import org.briarproject.api.clients.MessageTree;
import org.briarproject.api.sync.MessageId;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class MessageTreeTest {

	private static final Logger LOG =
			Logger.getLogger(MessageTreeTest.class.getName());

	private MessageTree<TestNode> tree;

	@Test
	public void testMessageTree() {
		tree = new MessageTreeImpl<>();
		testSimpleTree();
		tree.clear();
		testSimpleTree();
	}

	private void testSimpleTree() {
		TestNode[] nodes = new TestNode[5];
		for (int i = 0; i < nodes.length; i++) {
			nodes[i] = new TestNode();
		}
		/*
		Construct the following tree:
		4
		1 ->
		   0  ->
		       2
		   3
		 */
		nodes[0].setParentId(nodes[1].getId());
		nodes[2].setParentId(nodes[0].getId());
		nodes[3].setParentId(nodes[1].getId());
		long timestamp = System.currentTimeMillis();
		nodes[4].setTimestamp(timestamp - 5);
		nodes[1].setTimestamp(timestamp - 4);
		nodes[0].setTimestamp(timestamp - 3);
		nodes[3].setTimestamp(timestamp - 2);
		nodes[2].setTimestamp(timestamp - 1);
		// add all nodes except the last one
		tree.add(Arrays.asList(Arrays.copyOf(nodes, nodes.length-1)));
		tree.add(Collections.singletonList(nodes[nodes.length-1]));
		TestNode[] sortedNodes = tree.depthFirstOrder().toArray(new TestNode[5]);
		assertEquals(nodes[4], sortedNodes[0]);
		assertEquals(nodes[1], sortedNodes[1]);
		assertEquals(nodes[0], sortedNodes[2]);
		assertEquals(nodes[2], sortedNodes[3]);
		assertEquals(nodes[3], sortedNodes[4]);
	}

	private void printNodes(TestNode[] nodes, TestNode[] sortedNodes) {
		for (int i = 0; i < sortedNodes.length; i++) {
			for (int j = 0; j < nodes.length; j++) {
				if (sortedNodes[i] == nodes[j]) {
					LOG.info("index: " + j);
					break;
				}
			}
		}
	}

	class TestNode implements MessageTree.MessageNode {

		private final MessageId id = new MessageId(TestUtils.getRandomId());
		private MessageId parentId;
		private long timestamp;

		@Override
		public MessageId getId() {
			return id;
		}

		@Override
		public MessageId getParentId() {
			return parentId;
		}

		@Override
		public long getTimestamp() {
			return timestamp;
		}

		public void setParentId(MessageId parentId) {
			this.parentId = parentId;
		}

		public void setTimestamp(long timestamp) {
			this.timestamp = timestamp;
		}
	}
}
