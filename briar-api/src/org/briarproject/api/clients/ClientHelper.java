package org.briarproject.api.clients;

import org.briarproject.api.FormatException;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;

import java.util.Map;

public interface ClientHelper {

	void addLocalMessage(Message m, ClientId c, BdfDictionary metadata,
			boolean shared) throws DbException, FormatException;

	void addLocalMessage(Transaction txn, Message m, ClientId c,
			BdfDictionary metadata, boolean shared) throws DbException,
			FormatException;

	Message createMessage(GroupId g, long timestamp, BdfDictionary body)
			throws FormatException;

	Message createMessage(GroupId g, long timestamp, BdfList body)
			throws FormatException;

	BdfDictionary getMessageAsDictionary(MessageId m) throws DbException,
			FormatException;

	BdfDictionary getMessageAsDictionary(Transaction txn, MessageId m)
			throws DbException, FormatException;

	BdfList getMessageAsList(MessageId m) throws DbException, FormatException;

	BdfList getMessageAsList(Transaction txn, MessageId m) throws DbException,
			FormatException;

	BdfDictionary getGroupMetadataAsDictionary(GroupId g) throws DbException,
			FormatException;

	BdfDictionary getGroupMetadataAsDictionary(Transaction txn, GroupId g)
			throws DbException, FormatException;

	BdfDictionary getMessageMetadataAsDictionary(MessageId m) throws DbException,
			FormatException;

	BdfDictionary getMessageMetadataAsDictionary(Transaction txn, MessageId m)
			throws DbException, FormatException;

	Map<MessageId, BdfDictionary> getMessageMetatataAsDictionary(GroupId g)
			throws DbException, FormatException;

	Map<MessageId, BdfDictionary> getMessageMetadataAsDictionary(
			Transaction txn, GroupId g) throws DbException, FormatException;

	void mergeGroupMetadata(GroupId g, BdfDictionary metadata)
			throws DbException, FormatException;

	void mergeGroupMetadata(Transaction txn, GroupId g, BdfDictionary metadata)
			throws DbException, FormatException;

	void mergeMessageMetadata(MessageId m, BdfDictionary metadata)
			throws DbException, FormatException;

	void mergeMessageMetadata(Transaction txn, MessageId m,
			BdfDictionary metadata) throws DbException, FormatException;

	byte[] toByteArray(BdfDictionary dictionary) throws FormatException;

	byte[] toByteArray(BdfList list) throws FormatException;

	BdfDictionary toDictionary(byte[] b, int off, int len)
			throws FormatException;

	BdfList toList(byte[] b, int off, int len) throws FormatException;
}
