package org.briarproject.bramble.api.client;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;

import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Map;

@NotNullByDefault
public interface ClientHelper {

	void addLocalMessage(Message m, BdfDictionary metadata, boolean shared)
			throws DbException, FormatException;

	void addLocalMessage(Transaction txn, Message m, BdfDictionary metadata,
			boolean shared, boolean temporary)
			throws DbException, FormatException;

	Message createMessage(GroupId g, long timestamp, byte[] body);

	Message createMessage(GroupId g, long timestamp, BdfList body)
			throws FormatException;

	Message createMessageForStoringMetadata(GroupId g);

	Message getMessage(MessageId m) throws DbException;

	Message getMessage(Transaction txn, MessageId m) throws DbException;

	BdfList getMessageAsList(MessageId m) throws DbException, FormatException;

	BdfList getMessageAsList(Transaction txn, MessageId m) throws DbException,
			FormatException;

	BdfDictionary getGroupMetadataAsDictionary(GroupId g) throws DbException,
			FormatException;

	BdfDictionary getGroupMetadataAsDictionary(Transaction txn, GroupId g)
			throws DbException, FormatException;

	Collection<MessageId> getMessageIds(Transaction txn, GroupId g,
			BdfDictionary query) throws DbException, FormatException;

	BdfDictionary getMessageMetadataAsDictionary(MessageId m)
			throws DbException, FormatException;

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

	BdfDictionary toDictionary(TransportProperties transportProperties);

	BdfDictionary toDictionary(Map<TransportId, TransportProperties> map);

	BdfList toList(byte[] b, int off, int len) throws FormatException;

	BdfList toList(byte[] b) throws FormatException;

	BdfList toList(Message m) throws FormatException;

	BdfList toList(Author a);

	byte[] sign(String label, BdfList toSign, PrivateKey privateKey)
			throws FormatException, GeneralSecurityException;

	void verifySignature(byte[] signature, String label, BdfList signed,
			PublicKey publicKey)
			throws FormatException, GeneralSecurityException;

	Author parseAndValidateAuthor(BdfList author) throws FormatException;

	PublicKey parseAndValidateAgreementPublicKey(byte[] publicKeyBytes)
			throws FormatException;

	TransportProperties parseAndValidateTransportProperties(
			BdfDictionary properties) throws FormatException;

	Map<TransportId, TransportProperties> parseAndValidateTransportPropertiesMap(
			BdfDictionary properties) throws FormatException;

	/**
	 * Retrieves the contact ID from the group metadata of the given contact
	 * group.
	 */
	ContactId getContactId(Transaction txn, GroupId contactGroupId)
			throws DbException;

	/**
	 * Stores the given contact ID in the group metadata of the given contact
	 * group.
	 */
	void setContactId(Transaction txn, GroupId contactGroupId, ContactId c)
			throws DbException;
}
