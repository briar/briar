package org.briarproject.clients;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.BdfReader;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriter;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.MessageId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Inject;

import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;

class ClientHelperImpl implements ClientHelper {

	private final DatabaseComponent db;
	private final MessageFactory messageFactory;
	private final BdfReaderFactory bdfReaderFactory;
	private final BdfWriterFactory bdfWriterFactory;
	private final MetadataParser metadataParser;
	private final MetadataEncoder metadataEncoder;

	@Inject
	ClientHelperImpl(DatabaseComponent db, MessageFactory messageFactory,
			BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory, MetadataParser metadataParser,
			MetadataEncoder metadataEncoder) {
		this.db = db;
		this.messageFactory = messageFactory;
		this.bdfReaderFactory = bdfReaderFactory;
		this.bdfWriterFactory = bdfWriterFactory;
		this.metadataParser = metadataParser;
		this.metadataEncoder = metadataEncoder;
	}

	@Override
	public void addLocalMessage(Message m, ClientId c, BdfDictionary metadata,
			boolean shared) throws DbException, FormatException {
		Transaction txn = db.startTransaction(false);
		try {
			addLocalMessage(txn, m, c, metadata, shared);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void addLocalMessage(Transaction txn, Message m, ClientId c,
			BdfDictionary metadata, boolean shared)
			throws DbException, FormatException {
		db.addLocalMessage(txn, m, c, metadataEncoder.encode(metadata), shared);
	}

	@Override
	public Message createMessage(GroupId g, long timestamp, BdfDictionary body)
			throws FormatException {
		return messageFactory.createMessage(g, timestamp, toByteArray(body));
	}

	@Override
	public Message createMessage(GroupId g, long timestamp, BdfList body)
			throws FormatException {
		return messageFactory.createMessage(g, timestamp, toByteArray(body));
	}

	@Override
	public BdfList getMessageAsList(MessageId m) throws DbException,
			FormatException {
		BdfList list;
		Transaction txn = db.startTransaction(true);
		try {
			list = getMessageAsList(txn, m);
			txn.setComplete();
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
			txn.setComplete();
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
			txn.setComplete();
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
			txn.setComplete();
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
		return Collections.unmodifiableMap(parsed);
	}

	@Override
	public Map<MessageId, BdfDictionary> getMessageMetadataAsDictionary(
			GroupId g, BdfDictionary query) throws DbException,
			FormatException {
		Map<MessageId, BdfDictionary> map;
		Transaction txn = db.startTransaction(true);
		try {
			map = getMessageMetadataAsDictionary(txn, g, query);
			txn.setComplete();
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
		return Collections.unmodifiableMap(parsed);
	}

	@Override
	public void mergeGroupMetadata(GroupId g, BdfDictionary metadata)
			throws DbException, FormatException {
		Transaction txn = db.startTransaction(false);
		try {
			mergeGroupMetadata(txn, g, metadata);
			txn.setComplete();
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
			txn.setComplete();
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
	public void setMessageShared(Transaction txn, Message m, boolean shared)
			throws DbException {
		db.setMessageShared(txn, m, shared);
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
}
