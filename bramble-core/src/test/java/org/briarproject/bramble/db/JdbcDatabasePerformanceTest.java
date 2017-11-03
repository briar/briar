package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.ValidationManager.State;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.system.SystemClock;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestDatabaseConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_LENGTH;
import static org.briarproject.bramble.api.sync.ValidationManager.State.DELIVERED;
import static org.briarproject.bramble.api.sync.ValidationManager.State.PENDING;
import static org.briarproject.bramble.api.sync.ValidationManager.State.UNKNOWN;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getAuthor;
import static org.briarproject.bramble.test.TestUtils.getGroup;
import static org.briarproject.bramble.test.TestUtils.getLocalAuthor;
import static org.briarproject.bramble.test.TestUtils.getMean;
import static org.briarproject.bramble.test.TestUtils.getMedian;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getStandardDeviation;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.junit.Assert.assertTrue;

public abstract class JdbcDatabasePerformanceTest extends BrambleTestCase {

	private static final int ONE_MEGABYTE = 1024 * 1024;
	private static final int MAX_SIZE = 100 * ONE_MEGABYTE;
	private static final int CLIENT_ID_LENGTH = 100;
	private static final int METADATA_KEY_LENGTH = 100;
	private static final int METADATA_VALUE_LENGTH = 100;

	/**
	 * Skip test cases that create more than this many rows.
	 */
	private static final int MAX_ROWS = 100 * 1000;

	/**
	 * How many times to run the benchmark before measuring, to warm up the JIT.
	 */
	private static final int WARMUP_ITERATIONS = 100;

	/**
	 * How many times to run the benchmark while measuring.
	 */
	private static final int MEASUREMENT_ITERATIONS = 100;

	/**
	 * How much time to allow for background operations to complete after
	 * preparing the benchmark.
	 */
	private static final int SLEEP_BEFORE_MEASUREMENT_MS = 500;

	private final File testDir = getTestDirectory();
	private final File resultsFile = getResultsFile();

	protected abstract String getTestName();

	protected abstract JdbcDatabase createDatabase(DatabaseConfig config,
			Clock clock);

	@Before
	public void setUp() {
		assertTrue(testDir.mkdirs());
	}

	@After
	public void tearDown() {
		deleteTestDirectory(testDir);
	}

	private File getResultsFile() {
		long timestamp = System.currentTimeMillis();
		return new File(getTestName() + "-" + timestamp + ".tsv");
	}

	@Test
	public void testAddContact() throws Exception {
		for (int contacts : new int[] {0, 1, 10, 100, 1000}) {
			testAddContact(contacts);
		}
	}

	private void testAddContact(final int contacts) throws Exception {
		String name = "addContact(T, Author, AuthorId, boolean, boolean)";
		Map<String, Object> args =
				Collections.<String, Object>singletonMap("contacts", contacts);

		benchmark(name, args, new BenchmarkTask<Connection>() {

			private final LocalAuthor localAuthor = getLocalAuthor();

			@Override
			public void prepareBenchmark(Database<Connection> db)
					throws DbException {
				Connection txn = db.startTransaction();
				db.addLocalAuthor(txn, localAuthor);
				for (int i = 0; i < contacts; i++) {
					db.addContact(txn, getAuthor(), localAuthor.getId(), true,
							true);
				}
				db.commitTransaction(txn);
			}

			@Override
			public void runBenchmark(Database<Connection> db)
					throws DbException {
				Connection txn = db.startTransaction();
				db.addContact(txn, getAuthor(), localAuthor.getId(), true,
						true);
				db.commitTransaction(txn);
			}
		});
	}

	@Test
	public void testAddGroup() throws Exception {
		for (int groups : new int[] {0, 1, 10, 100, 1000}) {
			testAddGroup(groups);
		}
	}

	private void testAddGroup(final int groups) throws Exception {
		String name = "addGroup(T, group)";
		Map<String, Object> args =
				Collections.<String, Object>singletonMap("groups", groups);

		benchmark(name, args, new BenchmarkTask<Connection>() {

			private final ClientId clientId = getClientId();

			@Override
			public void prepareBenchmark(Database<Connection> db)
					throws DbException {
				Connection txn = db.startTransaction();
				for (int i = 0; i < groups; i++)
					db.addGroup(txn, getGroup(clientId));
				db.commitTransaction(txn);
			}

			@Override
			public void runBenchmark(Database<Connection> db)
					throws DbException {
				Connection txn = db.startTransaction();
				db.addGroup(txn, getGroup(clientId));
				db.commitTransaction(txn);
			}
		});
	}

	@Test
	public void testGetContacts() throws Exception {
		for (int contacts : new int[] {1, 10, 100, 1000}) {
			testGetContacts(contacts);
		}
	}

	private void testGetContacts(final int contacts) throws Exception {
		String name = "getContacts(T)";
		Map<String, Object> args =
				Collections.<String, Object>singletonMap("contacts", contacts);

		benchmark(name, args, new BenchmarkTask<Connection>() {

			@Override
			public void prepareBenchmark(Database<Connection> db)
					throws DbException {
				LocalAuthor localAuthor = getLocalAuthor();
				Connection txn = db.startTransaction();
				db.addLocalAuthor(txn, localAuthor);
				for (int i = 0; i < contacts; i++) {
					db.addContact(txn, getAuthor(), localAuthor.getId(), true,
							true);
				}
				db.commitTransaction(txn);
			}

			@Override
			public void runBenchmark(Database<Connection> db)
					throws DbException {
				Connection txn = db.startTransaction();
				db.getContacts(txn);
				db.commitTransaction(txn);
			}
		});
	}

	@Test
	public void testGetRawMessage() throws Exception {
		for (int messages : new int[] {1, 100, 10000}) {
			testGetRawMessage(messages);
		}
	}

	private void testGetRawMessage(final int messages) throws Exception {
		String name = "getRawMessage(T, MessageId)";
		Map<String, Object> args =
				Collections.<String, Object>singletonMap("messages", messages);

		benchmark(name, args, new BenchmarkTask<Connection>() {

			private MessageId messageId;

			@Override
			public void prepareBenchmark(Database<Connection> db)
					throws DbException {
				Group group = getGroup(getClientId());
				Connection txn = db.startTransaction();
				db.addGroup(txn, group);
				for (int i = 0; i < messages; i++) {
					Message m = getMessage(group.getId());
					if (i == 0) messageId = m.getId();
					db.addMessage(txn, m, DELIVERED, false);
				}
				db.commitTransaction(txn);
			}

			@Override
			public void runBenchmark(Database<Connection> db)
					throws DbException {
				Connection txn = db.startTransaction();
				db.getRawMessage(txn, messageId);
				db.commitTransaction(txn);
			}
		});
	}

	@Test
	public void testGetMessageIds() throws Exception {
		for (int groups : new int[] {1, 10, 100}) {
			for (int messagesPerGroup : new int[] {1, 10, 100}) {
				int rows = groups * messagesPerGroup;
				if (rows > MAX_ROWS) continue;
				testGetMessageIds(groups, messagesPerGroup);
			}
		}
	}

	private void testGetMessageIds(final int groups, final int messagesPerGroup)
			throws Exception {
		String name = "getMessageIds(T, GroupId)";
		Map<String, Object> args = new LinkedHashMap<String, Object>();
		args.put("groups", groups);
		args.put("messagesPerGroup", messagesPerGroup);

		benchmark(name, args, new BenchmarkTask<Connection>() {

			private GroupId groupId;

			@Override
			public void prepareBenchmark(Database<Connection> db)
					throws DbException {
				ClientId clientId = getClientId();
				Connection txn = db.startTransaction();
				for (int i = 0; i < groups; i++) {
					Group g = getGroup(clientId);
					if (i == 0) groupId = g.getId();
					db.addGroup(txn, g);
					for (int j = 0; j < messagesPerGroup; j++) {
						Message m = getMessage(g.getId());
						db.addMessage(txn, m, DELIVERED, false);
					}
				}
				db.commitTransaction(txn);
			}

			@Override
			public void runBenchmark(Database<Connection> db)
					throws DbException {
				Connection txn = db.startTransaction();
				db.getMessageIds(txn, groupId);
				db.commitTransaction(txn);
			}
		});
	}

	@Test
	public void testGetMessageIdsWithQuery() throws Exception {
		for (int groups : new int[] {1, 10, 100}) {
			for (int messagesPerGroup : new int[] {1, 10, 100}) {
				for (int keysPerMessage : new int[] {1, 10, 100}) {
					int rows = groups * messagesPerGroup * keysPerMessage;
					if (rows > MAX_ROWS) continue;
					for (int keysPerQuery : new int[] {1, 10}) {
						testGetMessageIdsWithQuery(groups, messagesPerGroup,
								keysPerMessage, keysPerQuery);
					}
				}
			}
		}
	}

	private void testGetMessageIdsWithQuery(final int groups,
			final int messagesPerGroup, final int keysPerMessage,
			final int keysPerQuery) throws Exception {
		String name = "getMessageIds(T, GroupId, Metadata)";
		Map<String, Object> args = new LinkedHashMap<String, Object>();
		args.put("groups", groups);
		args.put("messagesPerGroup", messagesPerGroup);
		args.put("keysPerMessage", keysPerMessage);
		args.put("keysPerQuery", keysPerQuery);

		benchmark(name, args, new BenchmarkTask<Connection>() {

			private final Metadata query = getMetadata(keysPerQuery);
			private GroupId groupId;

			@Override
			public void prepareBenchmark(Database<Connection> db)
					throws DbException {
				ClientId clientId = getClientId();
				Connection txn = db.startTransaction();
				for (int i = 0; i < groups; i++) {
					Group g = getGroup(clientId);
					if (i == 0) groupId = g.getId();
					db.addGroup(txn, g);
					for (int j = 0; j < messagesPerGroup; j++) {
						Message m = getMessage(g.getId());
						db.addMessage(txn, m, DELIVERED, false);
						Metadata meta = getMetadata(keysPerMessage);
						db.mergeMessageMetadata(txn, m.getId(), meta);
					}
				}
				db.commitTransaction(txn);
			}

			@Override
			public void runBenchmark(Database<Connection> db)
					throws DbException {
				Connection txn = db.startTransaction();
				db.getMessageIds(txn, groupId, query);
				db.commitTransaction(txn);
			}
		});
	}

	@Test
	public void testGetGroupMetadata() throws Exception {
		for (int groups : new int[] {1, 10, 100, 1000}) {
			for (int keysPerGroup : new int[] {1, 10, 100}) {
				int rows = groups * keysPerGroup;
				if (rows > MAX_ROWS) continue;
				testGetGroupMetadata(groups, keysPerGroup);
			}
		}
	}

	private void testGetGroupMetadata(final int groups, final int keysPerGroup)
			throws Exception {
		String name = "getGroupMetadata(T, GroupId)";
		Map<String, Object> args = new LinkedHashMap<String, Object>();
		args.put("groups", groups);
		args.put("keysPerGroup", keysPerGroup);

		benchmark(name, args, new BenchmarkTask<Connection>() {

			private GroupId groupId;

			@Override
			public void prepareBenchmark(Database<Connection> db)
					throws DbException {
				ClientId clientId = getClientId();
				Connection txn = db.startTransaction();
				for (int i = 0; i < groups; i++) {
					Group g = getGroup(clientId);
					if (i == 0) groupId = g.getId();
					db.addGroup(txn, g);
					Metadata meta = getMetadata(keysPerGroup);
					db.mergeGroupMetadata(txn, g.getId(), meta);
				}
				db.commitTransaction(txn);
			}

			@Override
			public void runBenchmark(Database<Connection> db)
					throws DbException {
				Connection txn = db.startTransaction();
				db.getGroupMetadata(txn, groupId);
				db.commitTransaction(txn);
			}
		});
	}

	@Test
	public void testGetMessageMetadataForGroup() throws Exception {
		for (int groups : new int[] {1, 10, 100}) {
			for (int messagesPerGroup : new int[] {1, 10, 100}) {
				for (int keysPerMessage : new int[] {1, 10, 100}) {
					int rows = groups * messagesPerGroup * keysPerMessage;
					if (rows > MAX_ROWS) continue;
					testGetMessageMetadataForGroup(groups, messagesPerGroup,
							keysPerMessage);
				}
			}
		}
	}

	private void testGetMessageMetadataForGroup(final int groups,
			final int messagesPerGroup, final int keysPerMessage)
			throws Exception {
		String name = "getMessageMetadata(T, GroupId)";
		Map<String, Object> args = new LinkedHashMap<String, Object>();
		args.put("groups", groups);
		args.put("messagesPerGroup", messagesPerGroup);
		args.put("keysPerMessage", keysPerMessage);

		benchmark(name, args, new BenchmarkTask<Connection>() {

			private GroupId groupId;

			@Override
			public void prepareBenchmark(Database<Connection> db)
					throws DbException {
				ClientId clientId = getClientId();
				Connection txn = db.startTransaction();
				for (int i = 0; i < groups; i++) {
					Group g = getGroup(clientId);
					if (i == 0) groupId = g.getId();
					db.addGroup(txn, g);
					for (int j = 0; j < messagesPerGroup; j++) {
						Message m = getMessage(g.getId());
						db.addMessage(txn, m, DELIVERED, false);
						Metadata meta = getMetadata(keysPerMessage);
						db.mergeMessageMetadata(txn, m.getId(), meta);
					}
				}
				db.commitTransaction(txn);
			}

			@Override
			public void runBenchmark(Database<Connection> db)
					throws DbException {
				Connection txn = db.startTransaction();
				db.getMessageMetadata(txn, groupId);
				db.commitTransaction(txn);
			}
		});
	}

	@Test
	public void testGetMessageMetadataForMessage() throws Exception {
		for (int messages : new int[] {1, 100, 10000}) {
			for (int keysPerMessage : new int[] {1, 10, 100}) {
				int rows = messages * keysPerMessage;
				if (rows > MAX_ROWS) continue;
				testGetMessageMetadataForMessage(messages, keysPerMessage);
			}
		}
	}

	private void testGetMessageMetadataForMessage(final int messages,
			final int keysPerMessage) throws Exception {
		String name = "getMessageMetadata(T, MessageId)";
		Map<String, Object> args = new LinkedHashMap<String, Object>();
		args.put("messages", messages);
		args.put("keysPerMessage", keysPerMessage);

		benchmark(name, args, new BenchmarkTask<Connection>() {

			private MessageId messageId;

			@Override
			public void prepareBenchmark(Database<Connection> db)
					throws DbException {
				Group g = getGroup(getClientId());
				Connection txn = db.startTransaction();
				db.addGroup(txn, g);
				for (int i = 0; i < messages; i++) {
					Message m = getMessage(g.getId());
					if (i == 0) messageId = m.getId();
					db.addMessage(txn, m, DELIVERED, false);
					Metadata meta = getMetadata(keysPerMessage);
					db.mergeMessageMetadata(txn, m.getId(), meta);
				}
				db.commitTransaction(txn);
			}

			@Override
			public void runBenchmark(Database<Connection> db)
					throws DbException {
				Connection txn = db.startTransaction();
				db.getMessageMetadata(txn, messageId);
				db.commitTransaction(txn);
			}
		});
	}

	@Test
	public void testGetMessagesToShare() throws Exception {
		for (int clients : new int[] {1, 10, 100}) {
			for (int groupsPerClient : new int[] {1, 10, 100}) {
				for (int messagesPerGroup : new int[] {1, 10, 100}) {
					int rows = clients * groupsPerClient * messagesPerGroup;
					if (rows > MAX_ROWS) continue;
					testGetMessagesToShare(clients, groupsPerClient,
							messagesPerGroup);
				}
			}
		}
	}

	private void testGetMessagesToShare(final int clients,
			final int groupsPerClient, final int messagesPerGroup)
			throws Exception {
		String name = "getMessagesToShare(T, ClientId)";
		Map<String, Object> args = new LinkedHashMap<String, Object>();
		args.put("clients", clients);
		args.put("groupsPerClient", groupsPerClient);
		args.put("messagesPerGroup", messagesPerGroup);

		benchmark(name, args, new BenchmarkTask<Connection>() {

			private ClientId clientId;

			@Override
			public void prepareBenchmark(Database<Connection> db)
					throws DbException {
				Random random = new Random();
				Connection txn = db.startTransaction();
				for (int i = 0; i < clients; i++) {
					ClientId c = getClientId();
					if (i == 0) clientId = c;
					for (int j = 0; j < groupsPerClient; j++) {
						Group g = getGroup(c);
						db.addGroup(txn, g);
						MessageId lastMessageId = null;
						for (int k = 0; k < messagesPerGroup; k++) {
							Message m = getMessage(g.getId());
							boolean shared = random.nextBoolean();
							db.addMessage(txn, m, DELIVERED, shared);
							if (lastMessageId != null) {
								db.addMessageDependency(txn, g.getId(),
										m.getId(), lastMessageId);
							}
							lastMessageId = m.getId();
						}
					}
				}
				db.commitTransaction(txn);
			}

			@Override
			public void runBenchmark(Database<Connection> db)
					throws DbException {
				Connection txn = db.startTransaction();
				db.getMessagesToShare(txn, clientId);
				db.commitTransaction(txn);
			}
		});
	}

	@Test
	public void testGetMessagesToValidate() throws Exception {
		for (int clients : new int[] {1, 10, 100}) {
			for (int groupsPerClient : new int[] {1, 10, 100}) {
				for (int messagesPerGroup : new int[] {1, 10, 100}) {
					int rows = clients * groupsPerClient * messagesPerGroup;
					if (rows > MAX_ROWS) continue;
					testGetMessagesToValidate(clients, groupsPerClient,
							messagesPerGroup);
				}
			}
		}
	}

	private void testGetMessagesToValidate(final int clients,
			final int groupsPerClient, final int messagesPerGroup)
			throws Exception {
		String name = "getMessagesToValidate(T, ClientId)";
		Map<String, Object> args = new LinkedHashMap<String, Object>();
		args.put("clients", clients);
		args.put("groupsPerClient", groupsPerClient);
		args.put("messagesPerGroup", messagesPerGroup);

		benchmark(name, args, new BenchmarkTask<Connection>() {

			private ClientId clientId;

			@Override
			public void prepareBenchmark(Database<Connection> db)
					throws DbException {
				Random random = new Random();
				Connection txn = db.startTransaction();
				for (int i = 0; i < clients; i++) {
					ClientId c = getClientId();
					if (i == 0) clientId = c;
					for (int j = 0; j < groupsPerClient; j++) {
						Group g = getGroup(c);
						db.addGroup(txn, g);
						for (int k = 0; k < messagesPerGroup; k++) {
							Message m = getMessage(g.getId());
							State s = random.nextBoolean() ? UNKNOWN : PENDING;
							db.addMessage(txn, m, s, false);
						}
					}
				}
				db.commitTransaction(txn);
			}

			@Override
			public void runBenchmark(Database<Connection> db)
					throws DbException {
				Connection txn = db.startTransaction();
				db.getMessagesToValidate(txn, clientId);
				db.commitTransaction(txn);
			}
		});
	}

	@Test
	public void testGetPendingMessages() throws Exception {
		for (int clients : new int[] {1, 10, 100}) {
			for (int groupsPerClient : new int[] {1, 10, 100}) {
				for (int messagesPerGroup : new int[] {1, 10, 100}) {
					int rows = clients * groupsPerClient * messagesPerGroup;
					if (rows > MAX_ROWS) continue;
					testGetPendingMessages(clients, groupsPerClient,
							messagesPerGroup);
				}
			}
		}
	}

	private void testGetPendingMessages(final int clients,
			final int groupsPerClient, final int messagesPerGroup)
			throws Exception {
		String name = "getPendingMessages(T, ClientId)";
		Map<String, Object> args = new LinkedHashMap<String, Object>();
		args.put("clients", clients);
		args.put("groupsPerClient", groupsPerClient);
		args.put("messagesPerGroup", messagesPerGroup);

		benchmark(name, args, new BenchmarkTask<Connection>() {

			private ClientId clientId;

			@Override
			public void prepareBenchmark(Database<Connection> db)
					throws DbException {
				Random random = new Random();
				Connection txn = db.startTransaction();
				for (int i = 0; i < clients; i++) {
					ClientId c = getClientId();
					if (i == 0) clientId = c;
					for (int j = 0; j < groupsPerClient; j++) {
						Group g = getGroup(c);
						db.addGroup(txn, g);
						for (int k = 0; k < messagesPerGroup; k++) {
							Message m = getMessage(g.getId());
							State s = random.nextBoolean() ? UNKNOWN : PENDING;
							db.addMessage(txn, m, s, false);
						}
					}
				}
				db.commitTransaction(txn);
			}

			@Override
			public void runBenchmark(Database<Connection> db)
					throws DbException {
				Connection txn = db.startTransaction();
				db.getPendingMessages(txn, clientId);
				db.commitTransaction(txn);
			}
		});
	}

	private void benchmark(String name, Map<String, Object> args,
			BenchmarkTask<Connection> task) throws Exception {
		for (int i = 0; i < WARMUP_ITERATIONS; i++) {
			Database<Connection> db = open();
			try {
				task.prepareBenchmark(db);
				task.runBenchmark(db);
			} finally {
				db.close();
			}
		}
		List<Long> durations = new ArrayList<Long>(MEASUREMENT_ITERATIONS);
		for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
			Database<Connection> db = open();
			try {
				task.prepareBenchmark(db);
				Thread.sleep(SLEEP_BEFORE_MEASUREMENT_MS);
				long start = System.nanoTime();
				task.runBenchmark(db);
				durations.add(System.nanoTime() - start);
			} finally {
				db.close();
			}
		}
		double meanMillis = getMean(durations) / 1000 / 1000;
		double medianMillis = getMedian(durations) / 1000 / 1000;
		double stdDevMillis = getStandardDeviation(durations) / 1000 / 1000;
		String result = name + '\t' + args + '\t' + meanMillis
				+ '\t' + medianMillis + '\t' + stdDevMillis;
		System.out.println(result);
		PrintWriter out =
				new PrintWriter(new FileOutputStream(resultsFile, true), true);
		out.println(result);
		out.close();
	}

	private Database<Connection> open() throws DbException {
		deleteTestDirectory(testDir);
		Database<Connection> db = createDatabase(
				new TestDatabaseConfig(testDir, MAX_SIZE), new SystemClock());
		db.open();
		return db;
	}

	private ClientId getClientId() {
		return new ClientId(getRandomString(CLIENT_ID_LENGTH));
	}

	private Message getMessage(GroupId groupId) {
		MessageId id = new MessageId(getRandomId());
		byte[] raw = getRandomBytes(MAX_MESSAGE_LENGTH);
		long timestamp = System.currentTimeMillis();
		return new Message(id, groupId, timestamp, raw);
	}

	private Metadata getMetadata(int keys) {
		Metadata meta = new Metadata();
		for (int i = 0; i < keys; i++) {
			String key = getRandomString(METADATA_KEY_LENGTH);
			byte[] value = getRandomBytes(METADATA_VALUE_LENGTH);
			meta.put(key, value);
		}
		return meta;
	}
}
