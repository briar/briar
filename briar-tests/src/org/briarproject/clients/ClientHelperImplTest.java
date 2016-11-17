package org.briarproject.clients;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.PrivateKey;
import org.briarproject.api.crypto.PublicKey;
import org.briarproject.api.crypto.Signature;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.BdfReader;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriter;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.MessageId;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.briarproject.TestUtils.getRandomBytes;
import static org.briarproject.TestUtils.getRandomId;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ClientHelperImplTest extends BriarTestCase {

	private final Mockery context = new Mockery();
	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final MessageFactory messageFactory =
			context.mock(MessageFactory.class);
	private final BdfReaderFactory bdfReaderFactory =
			context.mock(BdfReaderFactory.class);
	private final BdfWriterFactory bdfWriterFactory =
			context.mock(BdfWriterFactory.class);
	private final MetadataParser metadataParser =
			context.mock(MetadataParser.class);
	private final MetadataEncoder metadataEncoder =
			context.mock(MetadataEncoder.class);
	private final CryptoComponent cryptoComponent =
			context.mock(CryptoComponent.class);
	private final KeyParser keyParser = context.mock(KeyParser.class);
	private final Signature signature = context.mock(Signature.class);
	private final ClientHelper clientHelper;

	private final GroupId groupId = new GroupId(getRandomId());
	private final BdfDictionary dictionary = new BdfDictionary();
	private final long timestamp = 42L;
	private final byte[] rawMessage = getRandomBytes(42);
	private final MessageId messageId = new MessageId(getRandomId());
	private final Message message =
			new Message(messageId, groupId, timestamp, rawMessage);
	private final Metadata metadata = new Metadata();
	private final BdfList list = BdfList.of("Sign this!", getRandomBytes(42));
	private final String label = TestUtils.getRandomString(5);

	public ClientHelperImplTest() {
		clientHelper =
				new ClientHelperImpl(db, messageFactory, bdfReaderFactory,
						bdfWriterFactory, metadataParser, metadataEncoder,
						cryptoComponent);
	}

	@Test
	public void testAddLocalMessage() throws Exception {
		final boolean shared = true;
		final Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(metadataEncoder).encode(dictionary);
			will(returnValue(metadata));
			oneOf(db).addLocalMessage(txn, message, metadata, shared);
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		clientHelper.addLocalMessage(message, dictionary, shared);
		context.assertIsSatisfied();
	}

	@Test
	public void testCreateMessage() throws Exception {
		final byte[] bytes = expectToByteArray(list);

		context.checking(new Expectations() {{
			oneOf(messageFactory).createMessage(groupId, timestamp, bytes);
		}});

		clientHelper.createMessage(groupId, timestamp, list);
		context.assertIsSatisfied();
	}

	@Test
	public void testGetMessageAsList() throws Exception {
		final Transaction txn = new Transaction(null, true);

		expectToList(true);
		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getRawMessage(txn, messageId);
			will(returnValue(rawMessage));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		clientHelper.getMessageAsList(messageId);
		context.assertIsSatisfied();
	}

	@Test
	public void testGetGroupMetadataAsDictionary() throws Exception {
		final Transaction txn = new Transaction(null, true);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getGroupMetadata(txn, groupId);
			will(returnValue(metadata));
			oneOf(metadataParser).parse(metadata);
			will(returnValue(dictionary));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		assertEquals(dictionary,
				clientHelper.getGroupMetadataAsDictionary(groupId));
		context.assertIsSatisfied();
	}

	@Test
	public void testGetMessageMetadataAsDictionary() throws Exception {
		final Transaction txn = new Transaction(null, true);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getMessageMetadata(txn, messageId);
			will(returnValue(metadata));
			oneOf(metadataParser).parse(metadata);
			will(returnValue(dictionary));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		assertEquals(dictionary,
				clientHelper.getMessageMetadataAsDictionary(messageId));
		context.assertIsSatisfied();
	}

	@Test
	public void testGetMessageMetadataAsDictionaryMap() throws Exception {
		final Map<MessageId, BdfDictionary> map = new HashMap<>();
		map.put(messageId, dictionary);
		final Transaction txn = new Transaction(null, true);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(db).getMessageMetadata(txn, groupId);
			will(returnValue(Collections.singletonMap(messageId, metadata)));
			oneOf(metadataParser).parse(metadata);
			will(returnValue(dictionary));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		assertEquals(map,
				clientHelper.getMessageMetadataAsDictionary(groupId));
		context.assertIsSatisfied();
	}

	@Test
	public void testGetMessageMetadataAsDictionaryQuery() throws Exception {
		final Map<MessageId, BdfDictionary> map = new HashMap<>();
		map.put(messageId, dictionary);
		final BdfDictionary query =
				BdfDictionary.of(new BdfEntry("query", "me"));
		final Metadata queryMetadata = new Metadata();
		queryMetadata.put("query", getRandomBytes(42));
		final Transaction txn = new Transaction(null, true);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(true);
			will(returnValue(txn));
			oneOf(metadataEncoder).encode(query);
			will(returnValue(queryMetadata));
			oneOf(db).getMessageMetadata(txn, groupId, queryMetadata);
			will(returnValue(Collections.singletonMap(messageId, metadata)));
			oneOf(metadataParser).parse(metadata);
			will(returnValue(dictionary));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		assertEquals(map,
				clientHelper.getMessageMetadataAsDictionary(groupId, query));
		context.assertIsSatisfied();
	}

	@Test
	public void testMergeGroupMetadata() throws Exception {
		final Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(metadataEncoder).encode(dictionary);
			will(returnValue(metadata));
			oneOf(db).mergeGroupMetadata(txn, groupId, metadata);
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		clientHelper.mergeGroupMetadata(groupId, dictionary);
		context.assertIsSatisfied();
	}

	@Test
	public void testMergeMessageMetadata() throws Exception {
		final Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(metadataEncoder).encode(dictionary);
			will(returnValue(metadata));
			oneOf(db).mergeMessageMetadata(txn, messageId, metadata);
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		clientHelper.mergeMessageMetadata(messageId, dictionary);
		context.assertIsSatisfied();
	}

	@Test
	public void testToByteArray() throws Exception {
		byte[] bytes = expectToByteArray(list);

		assertArrayEquals(bytes, clientHelper.toByteArray(list));
		context.assertIsSatisfied();
	}

	@Test
	public void testToList() throws Exception {
		expectToList(true);

		assertEquals(list, clientHelper.toList(rawMessage));
		context.assertIsSatisfied();
	}

	@Test
	public void testToListWithNoEof() throws Exception {
		expectToList(false); // no EOF after list

		try {
			clientHelper.toList(rawMessage);
			fail();
		} catch (FormatException e) {
			// expected
			context.assertIsSatisfied();
		}
	}

	@Test
	public void testSign() throws Exception {
		final byte[] privateKeyBytes = getRandomBytes(42);
		final PrivateKey privateKey = context.mock(PrivateKey.class);
		final byte[] signed = getRandomBytes(42);

		final byte[] bytes = expectToByteArray(list);
		context.checking(new Expectations() {{
			oneOf(cryptoComponent).getSignatureKeyParser();
			will(returnValue(keyParser));
			oneOf(keyParser).parsePrivateKey(privateKeyBytes);
			will(returnValue(privateKey));
			oneOf(cryptoComponent).sign(label, bytes, privateKey);
			will(returnValue(signed));
		}});

		assertArrayEquals(signed,
				clientHelper.sign(label, list, privateKeyBytes));
		context.assertIsSatisfied();
	}

	@Test
	public void testVerifySignature() throws Exception {
		final PublicKey publicKey = context.mock(PublicKey.class);
		final byte[] publicKeyBytes = getRandomBytes(42);

		final byte[] bytes = expectToByteArray(list);
		context.checking(new Expectations() {{
			oneOf(cryptoComponent).getSignatureKeyParser();
			will(returnValue(keyParser));
			oneOf(keyParser).parsePublicKey(publicKeyBytes);
			will(returnValue(publicKey));
			oneOf(cryptoComponent).verify(label, bytes, publicKey, rawMessage);
			will(returnValue(true));
		}});

		clientHelper.verifySignature(label, rawMessage, publicKeyBytes, list);
		context.assertIsSatisfied();
	}

	@Test
	public void testVerifyWrongSignature() throws Exception {
		final PublicKey publicKey = context.mock(PublicKey.class);
		final byte[] publicKeyBytes = getRandomBytes(42);

		final byte[] bytes = expectToByteArray(list);
		context.checking(new Expectations() {{
			oneOf(cryptoComponent).getSignatureKeyParser();
			will(returnValue(keyParser));
			oneOf(keyParser).parsePublicKey(publicKeyBytes);
			will(returnValue(publicKey));
			oneOf(cryptoComponent).verify(label, bytes, publicKey, rawMessage);
			will(returnValue(false));
		}});

		try {
			clientHelper
					.verifySignature(label, rawMessage, publicKeyBytes, list);
			fail();
		} catch (GeneralSecurityException e) {
			// expected
			context.assertIsSatisfied();
		}
	}

	private byte[] expectToByteArray(final BdfList list) throws Exception {
		final BdfWriter bdfWriter = context.mock(BdfWriter.class);

		context.checking(new Expectations() {{
			oneOf(bdfWriterFactory)
					.createWriter(with(any(ByteArrayOutputStream.class)));
			will(returnValue(bdfWriter));
			oneOf(bdfWriter).writeList(list);
		}});
		return new byte[0];
	}

	private void expectToList(final boolean eof) throws Exception {
		final BdfReader bdfReader = context.mock(BdfReader.class);

		context.checking(new Expectations() {{
			oneOf(bdfReaderFactory)
					.createReader(with(any(InputStream.class)));
			will(returnValue(bdfReader));
			oneOf(bdfReader).readList();
			will(returnValue(list));
			oneOf(bdfReader).eof();
			will(returnValue(eof));
		}});
	}

}
