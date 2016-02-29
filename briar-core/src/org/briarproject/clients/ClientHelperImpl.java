package org.briarproject.clients;

import com.google.inject.Inject;

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
	public Message createMessage(GroupId g, long timestamp, BdfDictionary body)
			throws FormatException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BdfWriter writer = bdfWriterFactory.createWriter(out);
		try {
			writer.writeDictionary(body);
		} catch (FormatException e) {
			throw e;
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayOutputStream
			throw new RuntimeException(e);
		}
		byte[] raw = out.toByteArray();
		return messageFactory.createMessage(g, timestamp, raw);
	}

	@Override
	public Message createMessage(GroupId g, long timestamp, BdfList body)
			throws FormatException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BdfWriter writer = bdfWriterFactory.createWriter(out);
		try {
			writer.writeList(body);
		} catch (FormatException e) {
			throw e;
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayOutputStream
			throw new RuntimeException(e);
		}
		byte[] raw = out.toByteArray();
		return messageFactory.createMessage(g, timestamp, raw);
	}

	@Override
	public BdfDictionary getMessageAsDictionary(MessageId m)
			throws DbException, FormatException {
		BdfDictionary dictionary;
		Transaction txn = db.startTransaction();
		try {
			dictionary = getMessageAsDictionary(txn, m);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		return dictionary;
	}

	@Override
	public BdfDictionary getMessageAsDictionary(Transaction txn, MessageId m)
			throws DbException, FormatException {
		byte[] raw = db.getRawMessage(txn, m);
		if (raw == null) return null;
		ByteArrayInputStream in = new ByteArrayInputStream(raw);
		BdfReader reader = bdfReaderFactory.createReader(in);
		BdfDictionary dictionary;
		try {
			dictionary = reader.readDictionary();
			if (!reader.eof()) throw new FormatException();
		} catch (FormatException e) {
			throw e;
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayInputStream
			throw new RuntimeException(e);
		}
		return dictionary;
	}

	@Override
	public BdfList getMessageAsList(MessageId m)
			throws DbException, FormatException {
		BdfList list;
		Transaction txn = db.startTransaction();
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
		ByteArrayInputStream in = new ByteArrayInputStream(raw);
		BdfReader reader = bdfReaderFactory.createReader(in);
		BdfList list;
		try {
			list = reader.readList();
			if (!reader.eof()) throw new FormatException();
		} catch (FormatException e) {
			throw e;
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayInputStream
			throw new RuntimeException(e);
		}
		return list;
	}

	@Override
	public BdfDictionary getGroupMetadata(GroupId g)
			throws DbException, FormatException {
		BdfDictionary dictionary;
		Transaction txn = db.startTransaction();
		try {
			dictionary = getGroupMetadata(txn, g);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		return dictionary;
	}

	@Override
	public BdfDictionary getGroupMetadata(Transaction txn, GroupId g)
			throws DbException, FormatException {
		Metadata metadata = db.getGroupMetadata(txn, g);
		return metadataParser.parse(metadata);
	}

	@Override
	public BdfDictionary getMessageMetadata(MessageId m)
			throws DbException, FormatException {
		BdfDictionary dictionary;
		Transaction txn = db.startTransaction();
		try {
			dictionary = getMessageMetadata(txn, m);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		return dictionary;
	}

	@Override
	public BdfDictionary getMessageMetadata(Transaction txn, MessageId m)
			throws DbException, FormatException {
		Metadata metadata = db.getMessageMetadata(txn, m);
		return metadataParser.parse(metadata);
	}

	@Override
	public Map<MessageId, BdfDictionary> getMessageMetatata(GroupId g)
			throws DbException, FormatException {
		Map<MessageId, BdfDictionary> map;
		Transaction txn = db.startTransaction();
		try {
			map = getMessageMetadata(txn, g);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		return map;
	}

	@Override
	public Map<MessageId, BdfDictionary> getMessageMetadata(Transaction txn,
			GroupId g) throws DbException, FormatException {
		Map<MessageId, Metadata> raw = db.getMessageMetadata(txn, g);
		Map<MessageId, BdfDictionary> parsed =
				new HashMap<MessageId, BdfDictionary>(raw.size());
		for (Entry<MessageId, Metadata> e : raw.entrySet())
			parsed.put(e.getKey(), metadataParser.parse(e.getValue()));
		return Collections.unmodifiableMap(parsed);
	}

	@Override
	public void mergeGroupMetadata(GroupId g, BdfDictionary metadata)
			throws DbException, FormatException {
		Transaction txn = db.startTransaction();
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
		Transaction txn = db.startTransaction();
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
}
