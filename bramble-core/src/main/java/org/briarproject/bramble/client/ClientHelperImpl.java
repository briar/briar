package org.briarproject.bramble.client;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.BdfReader;
import org.briarproject.bramble.api.data.BdfReaderFactory;
import org.briarproject.bramble.api.data.BdfWriter;
import org.briarproject.bramble.api.data.BdfWriterFactory;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.MessageId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;

@Immutable
@NotNullByDefault
class ClientHelperImpl implements ClientHelper {

	/**
	 * Length in bytes of the random salt used for creating local messages for
	 * storing metadata.
	 */
	private static final int SALT_LENGTH = 32;

	private final DatabaseComponent db;
	private final MessageFactory messageFactory;
	private final BdfReaderFactory bdfReaderFactory;
	private final BdfWriterFactory bdfWriterFactory;
	private final MetadataParser metadataParser;
	private final MetadataEncoder metadataEncoder;
	private final CryptoComponent crypto;

	@Inject
	ClientHelperImpl(DatabaseComponent db, MessageFactory messageFactory,
			BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory, MetadataParser metadataParser,
			MetadataEncoder metadataEncoder, CryptoComponent crypto) {
		this.db = db;
		this.messageFactory = messageFactory;
		this.bdfReaderFactory = bdfReaderFactory;
		this.bdfWriterFactory = bdfWriterFactory;
		this.metadataParser = metadataParser;
		this.metadataEncoder = metadataEncoder;
		this.crypto = crypto;
	}

	@Override
	public void addLocalMessage(Message m, BdfDictionary metadata,
			boolean shared) throws DbException, FormatException {
		Transaction txn = db.startTransaction(false);
		try {
			addLocalMessage(txn, m, metadata, shared);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void addLocalMessage(Transaction txn, Message m,
			BdfDictionary metadata, boolean shared)
			throws DbException, FormatException {
		db.addLocalMessage(txn, m, metadataEncoder.encode(metadata), shared);
	}

	@Override
	public Message createMessage(GroupId g, long timestamp, BdfList body)
			throws FormatException {
		return messageFactory.createMessage(g, timestamp, toByteArray(body));
	}

	@Override
	public Message createMessageForStoringMetadata(GroupId g) {
		byte[] salt = new byte[SALT_LENGTH];
		crypto.getSecureRandom().nextBytes(salt);
		return messageFactory.createMessage(g, 0, salt);
	}

	@Override
	public Message getMessage(MessageId m) throws DbException {
		Message message;
		Transaction txn = db.startTransaction(true);
		try {
			message = getMessage(txn, m);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		return message;
	}

	@Override
	public Message getMessage(Transaction txn, MessageId m) throws DbException {
		byte[] raw = db.getRawMessage(txn, m);
		if (raw == null) return null;
		return messageFactory.createMessage(m, raw);
	}

	@Override
	public BdfList getMessageAsList(MessageId m) throws DbException,
			FormatException {
		BdfList list;
		Transaction txn = db.startTransaction(true);
		try {
			list = getMessageAsList(txn, m);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		return list;
	}

	@Override
	public BdfList getMessageAsList(Transaction txn, MessageId m)
			throws DbException, FormatException {
		byte[] raw = db.getRawMessage(txn, m);
		if (raw == null) return null;
		return toList(raw, MESSAGE_HEADER_LENGTH,
				raw.length - MESSAGE_HEADER_LENGTH);
	}

	@Override
	public BdfDictionary getGroupMetadataAsDictionary(GroupId g)
			throws DbException, FormatException {
		BdfDictionary dictionary;
		Transaction txn = db.startTransaction(true);
		try {
			dictionary = getGroupMetadataAsDictionary(txn, g);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		return dictionary;
	}

	@Override
	public BdfDictionary getGroupMetadataAsDictionary(Transaction txn,
			GroupId g) throws DbException, FormatException {
		Metadata metadata = db.getGroupMetadata(txn, g);
		return metadataParser.parse(metadata);
	}

	@Override
	public BdfDictionary getMessageMetadataAsDictionary(MessageId m)
			throws DbException, FormatException {
		BdfDictionary dictionary;
		Transaction txn = db.startTransaction(true);
		try {
			dictionary = getMessageMetadataAsDictionary(txn, m);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		return dictionary;
	}

	@Override
	public BdfDictionary getMessageMetadataAsDictionary(Transaction txn,
			MessageId m) throws DbException, FormatException {
		Metadata metadata = db.getMessageMetadata(txn, m);
		return metadataParser.parse(metadata);
	}

	@Override
	public Map<MessageId, BdfDictionary> getMessageMetadataAsDictionary(
			GroupId g) throws DbException, FormatException {
		Map<MessageId, BdfDictionary> map;
		Transaction txn = db.startTransaction(true);
		try {
			map = getMessageMetadataAsDictionary(txn, g);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		return map;
	}

	@Override
	public Map<MessageId, BdfDictionary> getMessageMetadataAsDictionary(
			Transaction txn, GroupId g) throws DbException, FormatException {
		Map<MessageId, Metadata> raw = db.getMessageMetadata(txn, g);
		Map<MessageId, BdfDictionary> parsed =
				new HashMap<MessageId, BdfDictionary>(raw.size());
		for (Entry<MessageId, Metadata> e : raw.entrySet())
			parsed.put(e.getKey(), metadataParser.parse(e.getValue()));
		return parsed;
	}

	@Override
	public Map<MessageId, BdfDictionary> getMessageMetadataAsDictionary(
			GroupId g, BdfDictionary query) throws DbException,
			FormatException {
		Map<MessageId, BdfDictionary> map;
		Transaction txn = db.startTransaction(true);
		try {
			map = getMessageMetadataAsDictionary(txn, g, query);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		return map;
	}

	@Override
	public Map<MessageId, BdfDictionary> getMessageMetadataAsDictionary(
			Transaction txn, GroupId g, BdfDictionary query) throws DbException,
			FormatException {
		Metadata metadata = metadataEncoder.encode(query);
		Map<MessageId, Metadata> raw = db.getMessageMetadata(txn, g, metadata);
		Map<MessageId, BdfDictionary> parsed =
				new HashMap<MessageId, BdfDictionary>(raw.size());
		for (Entry<MessageId, Metadata> e : raw.entrySet())
			parsed.put(e.getKey(), metadataParser.parse(e.getValue()));
		return parsed;
	}

	@Override
	public void mergeGroupMetadata(GroupId g, BdfDictionary metadata)
			throws DbException, FormatException {
		Transaction txn = db.startTransaction(false);
		try {
			mergeGroupMetadata(txn, g, metadata);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void mergeGroupMetadata(Transaction txn, GroupId g,
			BdfDictionary metadata) throws DbException, FormatException {
		db.mergeGroupMetadata(txn, g, metadataEncoder.encode(metadata));
	}

	@Override
	public void mergeMessageMetadata(MessageId m, BdfDictionary metadata)
			throws DbException, FormatException {
		Transaction txn = db.startTransaction(false);
		try {
			mergeMessageMetadata(txn, m, metadata);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void mergeMessageMetadata(Transaction txn, MessageId m,
			BdfDictionary metadata) throws DbException, FormatException {
		db.mergeMessageMetadata(txn, m, metadataEncoder.encode(metadata));
	}

	@Override
	public byte[] toByteArray(BdfDictionary dictionary) throws FormatException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BdfWriter writer = bdfWriterFactory.createWriter(out);
		try {
			writer.writeDictionary(dictionary);
		} catch (FormatException e) {
			throw e;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return out.toByteArray();
	}

	@Override
	public byte[] toByteArray(BdfList list) throws FormatException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BdfWriter writer = bdfWriterFactory.createWriter(out);
		try {
			writer.writeList(list);
		} catch (FormatException e) {
			throw e;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return out.toByteArray();
	}

	@Override
	public BdfDictionary toDictionary(byte[] b, int off, int len)
			throws FormatException {
		ByteArrayInputStream in = new ByteArrayInputStream(b, off, len);
		BdfReader reader = bdfReaderFactory.createReader(in);
		try {
			BdfDictionary dictionary = reader.readDictionary();
			if (!reader.eof()) throw new FormatException();
			return dictionary;
		} catch (FormatException e) {
			throw e;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public BdfList toList(byte[] b, int off, int len) throws FormatException {
		ByteArrayInputStream in = new ByteArrayInputStream(b, off, len);
		BdfReader reader = bdfReaderFactory.createReader(in);
		try {
			BdfList list = reader.readList();
			if (!reader.eof()) throw new FormatException();
			return list;
		} catch (FormatException e) {
			throw e;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public BdfList toList(byte[] b) throws FormatException {
		return toList(b, 0, b.length);
	}

	@Override
	public BdfList toList(Message m) throws FormatException {
		byte[] raw = m.getRaw();
		return toList(raw, MESSAGE_HEADER_LENGTH,
				raw.length - MESSAGE_HEADER_LENGTH);
	}

	@Override
	public byte[] sign(String label, BdfList toSign, byte[] privateKey)
			throws FormatException, GeneralSecurityException {
		return crypto.sign(label, toByteArray(toSign), privateKey);
	}

	@Override
	public void verifySignature(String label, byte[] sig, byte[] publicKey,
			BdfList signed) throws FormatException, GeneralSecurityException {
		if (!crypto.verify(label, toByteArray(signed), publicKey, sig)) {
			throw new GeneralSecurityException("Invalid signature");
		}
	}

}
