package org.briarproject.bramble.api.client;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;

import java.security.GeneralSecurityException;
import java.util.Map;

import javax.annotation.Nullable;

@NotNullByDefault
public interface ClientHelper {

	void addLocalMessage(Message m, BdfDictionary metadata, boolean shared)
			throws DbException, FormatException;

	void addLocalMessage(Transaction txn, Message m, BdfDictionary metadata,
			boolean shared) throws DbException, FormatException;

	Message createMessage(GroupId g, long timestamp, BdfList body)
			throws FormatException;

	Message createMessageForStoringMetadata(GroupId g);

	@Nullable
	Message getMessage(MessageId m) throws DbException;

	@Nullable
	Message getMessage(Transaction txn, MessageId m) throws DbException;

	@Nullable
	BdfList getMessageAsList(MessageId m) throws DbException, FormatException;

	@Nullable
	BdfList getMessageAsList(Transaction txn, MessageId m) throws DbException,
			FormatException;

	BdfDictionary getGroupMetadataAsDictionary(GroupId g) throws DbException,
			FormatException;

	BdfDictionary getGroupMetadataAsDictionary(Transaction txn, GroupId g)
			throws DbException, FormatException;

	BdfDictionary getMessageMetadataAsDictionary(MessageId m)
			throws DbException,
			FormatException;

	BdfDictionary getMessageMetadataAsDictionary(Transaction txn, MessageId m)
			throws DbException, FormatException;

	Map<MessageId, BdfDictionary> getMessageMetadataAsDictionary(GroupId g)
			throws DbException, FormatException;

	Map<MessageId, BdfDictionary> getMessageMetadataAsDictionary(
			Transaction txn, GroupId g) throws DbException, FormatException;

	Map<MessageId, BdfDictionary> getMessageMetadataAsDictionary(GroupId g,
			BdfDictionary query) throws DbException, FormatException;

	Map<MessageId, BdfDictionary> getMessageMetadataAsDictionary(
			Transaction txn, GroupId g, BdfDictionary query) throws DbException,
			FormatException;

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

	BdfList toList(byte[] b) throws FormatException;

	BdfList toList(Message m) throws FormatException;

	byte[] sign(String label, BdfList toSign, byte[] privateKey)
			throws FormatException, GeneralSecurityException;

	void verifySignature(String label, byte[] sig, byte[] publicKey,
			BdfList signed) throws FormatException, GeneralSecurityException;

}
