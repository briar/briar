package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.mailbox.InvalidMailboxIdException;
import org.briarproject.bramble.api.mailbox.MailboxAuthToken;
import org.briarproject.bramble.api.mailbox.MailboxFileId;
import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.mailbox.MailboxApi.ApiException;
import org.briarproject.bramble.mailbox.MailboxApi.MailboxContact;
import org.briarproject.bramble.mailbox.MailboxApi.MailboxFile;
import org.briarproject.bramble.mailbox.MailboxApi.TolerableFailureException;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.mailbox.lib.TestMailbox;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.briarproject.bramble.mailbox.AbstractMailboxIntegrationTest.URL_BASE;
import static org.briarproject.bramble.mailbox.MailboxIntegrationTestUtils.createMailboxApi;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.readBytes;
import static org.briarproject.bramble.test.TestUtils.writeBytes;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MailboxApiIntegrationTest extends BrambleTestCase {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Rule
	public TemporaryFolder dataDirectory = new TemporaryFolder();

	private TestMailbox mailbox;
	private MailboxAuthToken setupToken;

	private MailboxApi api;

	private MailboxProperties ownerProperties;

	@Before
	public void setUp()
			throws IOException, ApiException, InvalidMailboxIdException {
		mailbox = new TestMailbox(dataDirectory.getRoot());
		api = createMailboxApi(() -> mailbox.getPort());
		mailbox.startLifecycle();

		setupToken = MailboxAuthToken.fromString(mailbox.getSetupToken());

		assertNull(ownerProperties);
		MailboxProperties setupProperties = new MailboxProperties(
				URL_BASE, setupToken, new ArrayList<>());
		ownerProperties = api.setup(setupProperties);
	}

	@After
	public void tearDown() {
		mailbox.stopLifecycle(true);
	}

	@Test
	public void wipe() throws IOException, ApiException {
		api.wipeMailbox(ownerProperties);

		// check doesn't work anymore
		assertThrows(ApiException.class,
				() -> api.checkStatus(ownerProperties));

		// new setup doesn't work as mailbox is stopping
		MailboxProperties setupProperties = new MailboxProperties(
				URL_BASE, setupToken, new ArrayList<>());
		assertThrows(ApiException.class, () -> api.setup(setupProperties));
	}

	@Test
	public void testStatus() throws Exception {
		// Owner calls status endpoint
		assertTrue(api.checkStatus(ownerProperties));

		// Owner adds contact
		ContactId contactId = new ContactId(1);
		MailboxContact contact = getMailboxContact(contactId);
		MailboxProperties contactProperties = new MailboxProperties(
				ownerProperties.getOnion(), contact.token,
				new ArrayList<>(), contact.inboxId, contact.outboxId);
		api.addContact(ownerProperties, contact);

		// Contact calls status endpoint
		assertTrue(api.checkStatus(contactProperties));

		// Owner deletes contact again to leave clean state for other tests
		api.deleteContact(ownerProperties, contactId);
		assertEquals(emptyList(), api.getContacts(ownerProperties));
	}

	@Test
	public void testContactApi() throws Exception {
		ContactId contactId1 = new ContactId(1);
		ContactId contactId2 = new ContactId(2);
		MailboxContact mailboxContact1 = getMailboxContact(contactId1);
		MailboxContact mailboxContact2 = getMailboxContact(contactId2);

		// no contacts initially
		assertEquals(emptyList(), api.getContacts(ownerProperties));
		// added contact gets returned
		api.addContact(ownerProperties, mailboxContact1);
		assertEquals(singletonList(contactId1),
				api.getContacts(ownerProperties));
		// second contact also gets returned
		api.addContact(ownerProperties, mailboxContact2);
		assertEquals(Arrays.asList(contactId1, contactId2),
				api.getContacts(ownerProperties));

		// after both contacts get deleted, the list is empty again
		api.deleteContact(ownerProperties, contactId1);
		api.deleteContact(ownerProperties, contactId2);
		assertEquals(emptyList(), api.getContacts(ownerProperties));

		// deleting again is tolerable
		assertThrows(TolerableFailureException.class,
				() -> api.deleteContact(ownerProperties, contactId2));
	}

	@Test
	public void testFileManagementApi() throws Exception {
		// add contact, so we can leave each other files
		ContactId contactId = new ContactId(1);
		MailboxContact contact = getMailboxContact(contactId);
		MailboxProperties contactProperties = new MailboxProperties(
				ownerProperties.getOnion(), contact.token,
				new ArrayList<>(), contact.inboxId, contact.outboxId);
		api.addContact(ownerProperties, contact);

		// upload a file for our contact
		File file1 = folder.newFile();
		byte[] bytes1 = getRandomBytes(2048);
		writeBytes(file1, bytes1);
		api.addFile(ownerProperties, contact.inboxId, file1);

		// contact checks files
		List<MailboxFile> files1 =
				api.getFiles(contactProperties, contact.inboxId);
		assertEquals(1, files1.size());
		MailboxFileId fileName1 = files1.get(0).name;

		// owner can't check files
		assertThrows(TolerableFailureException.class, () ->
				api.getFiles(ownerProperties, contact.inboxId));

		// contact downloads file
		File file1downloaded = folder.newFile();
		api.getFile(contactProperties, contact.inboxId, fileName1,
				file1downloaded);
		assertArrayEquals(bytes1, readBytes(file1downloaded));

		// owner can't download file, even if knowing name
		File file1forbidden = folder.newFile();
		assertThrows(TolerableFailureException.class, () ->
				api.getFile(ownerProperties, contact.inboxId, fileName1,
						file1forbidden));
		assertEquals(0, file1forbidden.length());

		// owner can't delete file
		assertThrows(TolerableFailureException.class, () ->
				api.deleteFile(ownerProperties, contact.inboxId, fileName1));

		// contact deletes file
		api.deleteFile(contactProperties, contact.inboxId, fileName1);
		assertEquals(0,
				api.getFiles(contactProperties, contact.inboxId).size());

		// contact uploads two files for the owner
		File file2 = folder.newFile();
		File file3 = folder.newFile();
		byte[] bytes2 = getRandomBytes(2048);
		byte[] bytes3 = getRandomBytes(1024);
		writeBytes(file2, bytes2);
		writeBytes(file3, bytes3);
		api.addFile(contactProperties, contact.outboxId, file2);
		api.addFile(contactProperties, contact.outboxId, file3);

		// owner checks folders with available files
		List<MailboxFolderId> folders = api.getFolders(ownerProperties);
		assertEquals(singletonList(contact.outboxId), folders);

		// owner lists files in contact's outbox
		List<MailboxFile> files2 =
				api.getFiles(ownerProperties, contact.outboxId);
		assertEquals(2, files2.size());
		MailboxFileId file2name = files2.get(0).name;
		MailboxFileId file3name = files2.get(1).name;

		// contact can't list files in contact's outbox
		assertThrows(TolerableFailureException.class, () ->
				api.getFiles(contactProperties, contact.outboxId));

		// owner downloads both files from contact's outbox
		File file2downloaded = folder.newFile();
		File file3downloaded = folder.newFile();
		api.getFile(ownerProperties, contact.outboxId, file2name,
				file2downloaded);
		api.getFile(ownerProperties, contact.outboxId, file3name,
				file3downloaded);
		byte[] downloadedBytes2 = readBytes(file2downloaded);
		byte[] downloadedBytes3 = readBytes(file3downloaded);
		// file order is preserved (sorted by time),
		// so we know what file is which
		assertArrayEquals(bytes2, downloadedBytes2);
		assertArrayEquals(bytes3, downloadedBytes3);

		// contact can't download files again, even if knowing name
		File file2forbidden = folder.newFile();
		File file3forbidden = folder.newFile();
		assertThrows(TolerableFailureException.class, () ->
				api.getFile(contactProperties, contact.outboxId, file2name,
						file2forbidden));
		assertThrows(TolerableFailureException.class, () ->
				api.getFile(contactProperties, contact.outboxId, file3name,
						file3forbidden));
		assertEquals(0, file1forbidden.length());
		assertEquals(0, file2forbidden.length());

		// contact can't delete files in outbox
		assertThrows(TolerableFailureException.class, () ->
				api.deleteFile(contactProperties, contact.outboxId, file2name));
		assertThrows(TolerableFailureException.class, () ->
				api.deleteFile(contactProperties, contact.outboxId, file3name));

		// owner deletes files
		api.deleteFile(ownerProperties, contact.outboxId, file2name);
		api.deleteFile(ownerProperties, contact.outboxId, file3name);
		assertEquals(emptyList(),
				api.getFiles(ownerProperties, contact.outboxId));
		assertEquals(emptyList(), api.getFolders(ownerProperties));

		// deleting a non-existent file is tolerable
		assertThrows(TolerableFailureException.class, () ->
				api.deleteFile(ownerProperties, contact.outboxId, file3name));

		// owner deletes contact again to leave clean state for other tests
		api.deleteContact(ownerProperties, contactId);
		assertEquals(emptyList(), api.getContacts(ownerProperties));
	}

	private MailboxContact getMailboxContact(ContactId contactId) {
		MailboxAuthToken authToken = new MailboxAuthToken(getRandomId());
		MailboxFolderId inboxId = new MailboxFolderId(getRandomId());
		MailboxFolderId outboxId = new MailboxFolderId(getRandomId());
		return new MailboxContact(contactId, authToken, inboxId, outboxId);
	}

}
