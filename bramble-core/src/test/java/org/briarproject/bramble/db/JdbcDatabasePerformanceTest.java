package org.briarproject.bramble.db;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.ValidationManager.State;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.system.SystemClock;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestDatabaseConfig;
import org.briarproject.bramble.test.UTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.briarproject.bramble.api.sync.ValidationManager.State.DELIVERED;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getAuthor;
import static org.briarproject.bramble.test.TestUtils.getGroup;
import static org.briarproject.bramble.test.TestUtils.getLocalAuthor;
import static org.briarproject.bramble.test.TestUtils.getMean;
import static org.briarproject.bramble.test.TestUtils.getMedian;
import static org.briarproject.bramble.test.TestUtils.getMessage;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getStandardDeviation;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.briarproject.bramble.test.UTest.Result.INCONCLUSIVE;
import static org.briarproject.bramble.test.UTest.Z_CRITICAL_0_1;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.junit.Assert.assertTrue;

public abstract class JdbcDatabasePerformanceTest extends BrambleTestCase {

	private static final int ONE_MEGABYTE = 1024 * 1024;
	private static final int MAX_SIZE = 100 * ONE_MEGABYTE;

	/**
	 * How many contacts to simulate.
	 */
	private static final int CONTACTS = 20;

	/**
	 * How many clients to simulate. Briar has nine: transport properties,
	 * introductions, messaging, forums, forum sharing, blogs,
	 * blog sharing, private groups, and private group sharing.
	 */
	private static final int CLIENTS = 10;
	private static final int CLIENT_ID_LENGTH = 50;

	/**
	 * How many groups to simulate for each contact. Briar has seven:
	 * transport properties, introductions, messaging, forum sharing, blog
	 * sharing, private group sharing, and the contact's blog.
	 */
	private static final int GROUPS_PER_CONTACT = 10;

	/**
	 * How many local groups to simulate. Briar has three: transport
	 * properties, introductions and RSS feeds.
	 */
	private static final int LOCAL_GROUPS = 5;

	private static final int MESSAGES_PER_GROUP = 20;
	private static final int METADATA_KEYS_PER_GROUP = 5;
	private static final int METADATA_KEYS_PER_MESSAGE = 5;
	private static final int METADATA_KEY_LENGTH = 10;
	private static final int METADATA_VALUE_LENGTH = 100;

	/**
	 * How many times to run each benchmark while measuring.
	 */
	private static final int MEASUREMENT_ITERATIONS = 100;

	private final File testDir = getTestDirectory();
	private final Random random = new Random();

	private List<ClientId> clientIds;
	private List<Group> groups;
	private List<Message> messages;
	private Map<GroupId, List<Metadata>> messageMeta;

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

	@Test
	public void testGetContacts() throws Exception {
		String name = "getContacts(T)";
		benchmark(name, db -> {
			Connection txn = db.startTransaction();
			db.getContacts(txn);
			db.commitTransaction(txn);
		});
	}

	@Test
	public void testGetRawMessage() throws Exception {
		String name = "getRawMessage(T, MessageId)";
		benchmark(name, db -> {
			Connection txn = db.startTransaction();
			db.getRawMessage(txn, pickRandom(messages).getId());
			db.commitTransaction(txn);
		});
	}

	@Test
	public void testGetMessageIds() throws Exception {
		String name = "getMessageIds(T, GroupId)";
		benchmark(name, db -> {
			Connection txn = db.startTransaction();
			db.getMessageIds(txn, pickRandom(groups).getId());
			db.commitTransaction(txn);
		});
	}

	@Test
	public void testGetMessageIdsWithMatchingQuery() throws Exception {
		String name = "getMessageIds(T, GroupId, Metadata)";
		benchmark(name, db -> {
			Connection txn = db.startTransaction();
			GroupId g = pickRandom(groups).getId();
			db.getMessageIds(txn, g, pickRandom(messageMeta.get(g)));
			db.commitTransaction(txn);
		});
	}

	@Test
	public void testGetMessageIdsWithNonMatchingQuery() throws Exception {
		String name = "getMessageIds(T, GroupId, Metadata)";
		benchmark(name, db -> {
			Connection txn = db.startTransaction();
			Metadata query = getMetadata(METADATA_KEYS_PER_MESSAGE);
			db.getMessageIds(txn, pickRandom(groups).getId(), query);
			db.commitTransaction(txn);
		});
	}

	@Test
	public void testGetGroupMetadata() throws Exception {
		String name = "getGroupMetadata(T, GroupId)";
		benchmark(name, db -> {
			Connection txn = db.startTransaction();
			db.getGroupMetadata(txn, pickRandom(groups).getId());
			db.commitTransaction(txn);
		});
	}

	@Test
	public void testGetMessageMetadataForGroup() throws Exception {
		String name = "getMessageMetadata(T, GroupId)";
		benchmark(name, db -> {
			Connection txn = db.startTransaction();
			db.getMessageMetadata(txn, pickRandom(groups).getId());
			db.commitTransaction(txn);
		});
	}

	@Test
	public void testGetMessageMetadataForMessage() throws Exception {
		String name = "getMessageMetadata(T, MessageId)";
		benchmark(name, db -> {
			Connection txn = db.startTransaction();
			db.getMessageMetadata(txn, pickRandom(messages).getId());
			db.commitTransaction(txn);
		});
	}

	@Test
	public void testGetMessagesToShare() throws Exception {
		String name = "getMessagesToShare(T, ClientId)";
		benchmark(name, db -> {
			Connection txn = db.startTransaction();
			db.getMessagesToShare(txn, pickRandom(clientIds));
			db.commitTransaction(txn);
		});
	}

	@Test
	public void testGetMessagesToValidate() throws Exception {
		String name = "getMessagesToValidate(T, ClientId)";
		benchmark(name, db -> {
			Connection txn = db.startTransaction();
			db.getMessagesToValidate(txn, pickRandom(clientIds));
			db.commitTransaction(txn);
		});
	}

	@Test
	public void testGetPendingMessages() throws Exception {
		String name = "getPendingMessages(T, ClientId)";
		benchmark(name, db -> {
			Connection txn = db.startTransaction();
			db.getPendingMessages(txn, pickRandom(clientIds));
			db.commitTransaction(txn);
		});
	}

	private <T> T pickRandom(List<T> list) {
		return list.get(random.nextInt(list.size()));
	}

	private void benchmark(String name,
			BenchmarkTask<Database<Connection>> task) throws Exception {
		deleteTestDirectory(testDir);
		Database<Connection> db = openDatabase();
		populateDatabase(db);
		db.close();
		db = openDatabase();
		// Measure the first iteration
		long start = System.nanoTime();
		task.run(db);
		long firstDuration = System.nanoTime() - start;
		// Measure blocks of iterations until we reach a steady state
		List<Double> oldDurations = measureBlock(db, task);
		List<Double> durations = measureBlock(db, task);
		int blocks = 2;
		while (UTest.test(oldDurations, durations, Z_CRITICAL_0_1)
				!= INCONCLUSIVE) {
			oldDurations = durations;
			durations = measureBlock(db, task);
			blocks++;
		}
		db.close();
		String result = String.format("%s\t%d\t%,d\t%,d\t%,d\t%,d", name,
				blocks, firstDuration, (long) getMean(durations),
				(long) getMedian(durations),
				(long) getStandardDeviation(durations));
		System.out.println(result);
		File resultsFile = new File(getTestName() + ".tsv");
		PrintWriter out =
				new PrintWriter(new FileOutputStream(resultsFile, true), true);
		out.println(new Date() + "\t" + result);
		out.close();
	}

	private Database<Connection> openDatabase() throws DbException {
		Database<Connection> db = createDatabase(
				new TestDatabaseConfig(testDir, MAX_SIZE), new SystemClock());
		db.open();
		return db;
	}

	private void populateDatabase(Database<Connection> db) throws DbException {
		clientIds = new ArrayList<>();
		groups = new ArrayList<>();
		messages = new ArrayList<>();
		messageMeta = new HashMap<>();

		for (int i = 0; i < CLIENTS; i++) clientIds.add(getClientId());

		Connection txn = db.startTransaction();
		LocalAuthor localAuthor = getLocalAuthor();
		db.addLocalAuthor(txn, localAuthor);
		for (int i = 0; i < CONTACTS; i++) {
			Author a = getAuthor();
			ContactId contactId = db.addContact(txn, a, localAuthor.getId(),
					random.nextBoolean(), true);
			for (int j = 0; j < GROUPS_PER_CONTACT; j++) {
				Group g = getGroup(clientIds.get(j % CLIENTS));
				groups.add(g);
				messageMeta.put(g.getId(), new ArrayList<>());
				db.addGroup(txn, g);
				db.addGroupVisibility(txn, contactId, g.getId(), true);
				Metadata gm = getMetadata(METADATA_KEYS_PER_GROUP);
				db.mergeGroupMetadata(txn, g.getId(), gm);
				for (int k = 0; k < MESSAGES_PER_GROUP; k++) {
					Message m = getMessage(g.getId());
					messages.add(m);
					State state = State.fromValue(random.nextInt(4));
					db.addMessage(txn, m, state, random.nextBoolean());
					Metadata mm = getMetadata(METADATA_KEYS_PER_MESSAGE);
					messageMeta.get(g.getId()).add(mm);
					db.mergeMessageMetadata(txn, m.getId(), mm);
				}
			}
		}
		for (int i = 0; i < LOCAL_GROUPS; i++) {
			Group g = getGroup(clientIds.get(i % CLIENTS));
			groups.add(g);
			messageMeta.put(g.getId(), new ArrayList<>());
			db.addGroup(txn, g);
			Metadata gm = getMetadata(METADATA_KEYS_PER_GROUP);
			db.mergeGroupMetadata(txn, g.getId(), gm);
			for (int j = 0; j < MESSAGES_PER_GROUP; j++) {
				Message m = getMessage(g.getId());
				messages.add(m);
				db.addMessage(txn, m, DELIVERED, false);
				Metadata mm = getMetadata(METADATA_KEYS_PER_MESSAGE);
				messageMeta.get(g.getId()).add(mm);
				db.mergeMessageMetadata(txn, m.getId(), mm);
			}
		}
		db.commitTransaction(txn);
	}

	private List<Double> measureBlock(Database<Connection> db,
			BenchmarkTask<Database<Connection>> task) throws Exception {
		List<Double> durations = new ArrayList<>(MEASUREMENT_ITERATIONS);
		for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
			long start = System.nanoTime();
			task.run(db);
			durations.add((double) (System.nanoTime() - start));
		}
		return durations;
	}

	private ClientId getClientId() {
		return new ClientId(getRandomString(CLIENT_ID_LENGTH));
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
