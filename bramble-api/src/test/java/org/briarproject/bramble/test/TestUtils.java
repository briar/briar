package org.briarproject.bramble.test;

import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.util.IoUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_GROUP_DESCRIPTOR_LENGTH;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;
import static org.briarproject.bramble.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.bramble.util.StringUtils.getRandomString;

public class TestUtils {

	private static final AtomicInteger nextTestDir =
			new AtomicInteger((int) (Math.random() * 1000 * 1000));
	private static final Random random = new Random();

	public static File getTestDirectory() {
		int name = nextTestDir.getAndIncrement();
		return new File("test.tmp/" + name);
	}

	public static void deleteTestDirectory(File testDir) {
		IoUtils.deleteFileOrDir(testDir);
		testDir.getParentFile().delete(); // Delete if empty
	}

	public static byte[] getRandomBytes(int length) {
		byte[] b = new byte[length];
		random.nextBytes(b);
		return b;
	}

	public static byte[] getRandomId() {
		return getRandomBytes(UniqueId.LENGTH);
	}

	public static SecretKey getSecretKey() {
		return new SecretKey(getRandomBytes(SecretKey.LENGTH));
	}

	public static LocalAuthor getLocalAuthor() {
		return getLocalAuthor(1 + random.nextInt(MAX_AUTHOR_NAME_LENGTH));
	}

	public static LocalAuthor getLocalAuthor(int nameLength) {
		AuthorId id = new AuthorId(getRandomId());
		String name = getRandomString(nameLength);
		byte[] publicKey = getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
		byte[] privateKey = getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
		long created = System.currentTimeMillis();
		return new LocalAuthor(id, name, publicKey, privateKey, created);
	}

	public static Author getAuthor() {
		return getAuthor(1 + random.nextInt(MAX_AUTHOR_NAME_LENGTH));
	}

	public static Author getAuthor(int nameLength) {
		AuthorId id = new AuthorId(getRandomId());
		String name = getRandomString(nameLength);
		byte[] publicKey = getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
		return new Author(id, name, publicKey);
	}

	public static Group getGroup(ClientId clientId) {
		int descriptorLength = 1 + random.nextInt(MAX_GROUP_DESCRIPTOR_LENGTH);
		return getGroup(clientId, descriptorLength);
	}

	public static Group getGroup(ClientId clientId, int descriptorLength) {
		GroupId groupId = new GroupId(getRandomId());
		byte[] descriptor = getRandomBytes(descriptorLength);
		return new Group(groupId, clientId, descriptor);
	}

	public static Message getMessage(GroupId groupId) {
		int bodyLength = 1 + random.nextInt(MAX_MESSAGE_BODY_LENGTH);
		return getMessage(groupId, MESSAGE_HEADER_LENGTH + bodyLength);
	}

	public static Message getMessage(GroupId groupId, int rawLength) {
		MessageId id = new MessageId(getRandomId());
		byte[] raw = getRandomBytes(rawLength);
		long timestamp = System.currentTimeMillis();
		return new Message(id, groupId, timestamp, raw);
	}

	public static double getMedian(Collection<? extends Number> samples) {
		int size = samples.size();
		if (size == 0) throw new IllegalArgumentException();
		List<Double> sorted = new ArrayList<>(size);
		for (Number n : samples) sorted.add(n.doubleValue());
		Collections.sort(sorted);
		if (size % 2 == 1) return sorted.get(size / 2);
		double low = sorted.get(size / 2 - 1), high = sorted.get(size / 2);
		return (low + high) / 2;
	}

	public static double getMean(Collection<? extends Number> samples) {
		if (samples.isEmpty()) throw new IllegalArgumentException();
		double sum = 0;
		for (Number n : samples) sum += n.doubleValue();
		return sum / samples.size();
	}

	public static double getVariance(Collection<? extends Number> samples) {
		if (samples.size() < 2) throw new IllegalArgumentException();
		double mean = getMean(samples);
		double sumSquareDiff = 0;
		for (Number n : samples) {
			double diff = n.doubleValue() - mean;
			sumSquareDiff += diff * diff;
		}
		return sumSquareDiff / (samples.size() - 1);
	}

	public static double getStandardDeviation(
			Collection<? extends Number> samples) {
		return Math.sqrt(getVariance(samples));
	}
}
