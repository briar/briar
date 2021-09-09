package org.briarproject.briar.test;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.Identity;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.BrambleIntegrationTest;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.briar.api.autodelete.AutoDeleteManager;
import org.briarproject.briar.api.blog.BlogFactory;
import org.briarproject.briar.api.blog.BlogPostFactory;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.forum.ForumPostFactory;
import org.briarproject.briar.api.privategroup.GroupMessageFactory;
import org.briarproject.briar.api.privategroup.PrivateGroupFactory;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationFactory;
import org.junit.After;
import org.junit.Before;

import java.io.File;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static junit.framework.Assert.assertNotNull;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.junit.Assert.assertEquals;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class BriarIntegrationTest<C extends BriarIntegrationTestComponent>
		extends BrambleIntegrationTest<C> {

	@Nullable
	protected ContactId contactId1From2, contactId2From1;
	protected ContactId contactId0From1, contactId0From2, contactId1From0,
			contactId2From0;
	protected Contact contact0From1, contact0From2, contact1From0,
			contact2From0;
	protected LocalAuthor author0, author1, author2;
	protected ContactManager contactManager0, contactManager1, contactManager2;
	private IdentityManager identityManager0, identityManager1,
			identityManager2;
	protected DatabaseComponent db0, db1, db2;
	protected MessageTracker messageTracker0, messageTracker1, messageTracker2;

	private LifecycleManager lifecycleManager0, lifecycleManager1,
			lifecycleManager2;

	@Inject
	protected CryptoComponent crypto;
	@Inject
	protected ClientHelper clientHelper;
	@Inject
	protected MessageFactory messageFactory;
	@Inject
	protected ContactGroupFactory contactGroupFactory;
	@Inject
	protected PrivateGroupFactory privateGroupFactory;
	@Inject
	protected GroupMessageFactory groupMessageFactory;
	@Inject
	protected GroupInvitationFactory groupInvitationFactory;
	@Inject
	protected BlogFactory blogFactory;
	@Inject
	protected BlogPostFactory blogPostFactory;
	@Inject
	protected ForumPostFactory forumPostFactory;

	protected C c0, c1, c2;

	private final File testDir = TestUtils.getTestDirectory();
	private final String AUTHOR0 = "Author 0";
	private final String AUTHOR1 = "Author 1";
	private final String AUTHOR2 = "Author 2";
	private final SecretKey rootKey0_1 = getSecretKey();
	private final SecretKey rootKey0_2 = getSecretKey();
	private final SecretKey rootKey1_2 = getSecretKey();

	protected final File t0Dir = new File(testDir, AUTHOR0);
	protected final File t1Dir = new File(testDir, AUTHOR1);
	protected final File t2Dir = new File(testDir, AUTHOR2);

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		createComponents();

		identityManager0 = c0.getIdentityManager();
		identityManager1 = c1.getIdentityManager();
		identityManager2 = c2.getIdentityManager();
		contactManager0 = c0.getContactManager();
		contactManager1 = c1.getContactManager();
		contactManager2 = c2.getContactManager();
		messageTracker0 = c0.getMessageTracker();
		messageTracker1 = c1.getMessageTracker();
		messageTracker2 = c2.getMessageTracker();
		db0 = c0.getDatabaseComponent();
		db1 = c1.getDatabaseComponent();
		db2 = c2.getDatabaseComponent();

		createAndRegisterIdentities();
		startLifecycles();
		listenToEvents();
		addDefaultContacts();
	}

	abstract protected void createComponents();

	private void startLifecycles() throws InterruptedException {
		// Start the lifecycle manager and wait for it to finish starting
		lifecycleManager0 = c0.getLifecycleManager();
		lifecycleManager1 = c1.getLifecycleManager();
		lifecycleManager2 = c2.getLifecycleManager();
		lifecycleManager0.startServices(getSecretKey());
		lifecycleManager1.startServices(getSecretKey());
		lifecycleManager2.startServices(getSecretKey());
		lifecycleManager0.waitForStartup();
		lifecycleManager1.waitForStartup();
		lifecycleManager2.waitForStartup();
	}

	private void listenToEvents() {
		addEventListener(c0);
		addEventListener(c1);
		addEventListener(c2);
	}

	private void createAndRegisterIdentities() {
		Identity identity0 = identityManager0.createIdentity(AUTHOR0);
		identityManager0.registerIdentity(identity0);
		author0 = identity0.getLocalAuthor();
		Identity identity1 = identityManager0.createIdentity(AUTHOR1);
		identityManager1.registerIdentity(identity1);
		author1 = identity1.getLocalAuthor();
		Identity identity2 = identityManager0.createIdentity(AUTHOR2);
		identityManager2.registerIdentity(identity2);
		author2 = identity2.getLocalAuthor();
	}

	protected void addDefaultContacts() throws Exception {
		contactId1From0 = contactManager0.addContact(author1, author0.getId(),
				rootKey0_1, c0.getClock().currentTimeMillis(), true, true,
				true);
		contact1From0 = contactManager0.getContact(contactId1From0);
		contactId0From1 = contactManager1.addContact(author0, author1.getId(),
				rootKey0_1, c1.getClock().currentTimeMillis(), false, true,
				true);
		contact0From1 = contactManager1.getContact(contactId0From1);
		contactId2From0 = contactManager0.addContact(author2, author0.getId(),
				rootKey0_2, c0.getClock().currentTimeMillis(), true, true,
				true);
		contact2From0 = contactManager0.getContact(contactId2From0);
		contactId0From2 = contactManager2.addContact(author0, author2.getId(),
				rootKey0_2, c2.getClock().currentTimeMillis(), false, true,
				true);
		contact0From2 = contactManager2.getContact(contactId0From2);

		// Sync initial client versioning updates
		sync0To1(1, true);
		sync1To0(1, true);
		sync0To1(1, true);
		ack1To0(1);

		sync0To2(1, true);
		sync2To0(1, true);
		sync0To2(1, true);
		ack2To0(1);
	}

	protected void addContacts1And2() throws Exception {
		addContacts1And2(false);
	}

	protected void addContacts1And2(boolean haveTransportProperties)
			throws Exception {
		contactId2From1 = contactManager1.addContact(author2, author1.getId(),
				rootKey1_2, c1.getClock().currentTimeMillis(), true, true,
				true);
		contactId1From2 = contactManager2.addContact(author1, author2.getId(),
				rootKey1_2, c2.getClock().currentTimeMillis(), false, true,
				true);

		// Sync initial client versioning updates
		sync1To2(1, true);
		sync2To1(1, true);
		if (haveTransportProperties) {
			sync1To2(2, true);
			sync2To1(1, true);
			ack1To2(1);
		} else {
			sync1To2(1, true);
			ack2To1(1);
		}
	}

	protected void assertMessageState(ConversationMessageHeader h, boolean read,
			boolean sent, boolean seen) {
		assertEquals("read", read, h.isRead());
		assertEquals("sent", sent, h.isSent());
		assertEquals("seen", seen, h.isSeen());
	}

	@After
	@Override
	public void tearDown() throws Exception {
		stopLifecycles();
		super.tearDown();
	}

	private void stopLifecycles() throws InterruptedException {
		// Clean up
		lifecycleManager0.stopServices();
		lifecycleManager1.stopServices();
		lifecycleManager2.stopServices();
		lifecycleManager0.waitForShutdown();
		lifecycleManager1.waitForShutdown();
		lifecycleManager2.waitForShutdown();
	}

	protected void sync0To1(int num, boolean valid) throws Exception {
		syncMessage(c0, c1, contactId1From0, num, valid);
	}

	protected void sync0To2(int num, boolean valid) throws Exception {
		syncMessage(c0, c2, contactId2From0, num, valid);
	}

	protected void sync1To0(int num, boolean valid) throws Exception {
		syncMessage(c1, c0, contactId0From1, num, valid);
	}

	protected void sync2To0(int num, boolean valid) throws Exception {
		syncMessage(c2, c0, contactId0From2, num, valid);
	}

	protected void sync2To1(int num, boolean valid) throws Exception {
		assertNotNull(contactId1From2);
		syncMessage(c2, c1, contactId1From2, num, valid);
	}

	protected void sync1To2(int num, boolean valid) throws Exception {
		assertNotNull(contactId2From1);
		syncMessage(c1, c2, contactId2From1, num, valid);
	}

	protected void ack0To1(int num) throws Exception {
		sendAcks(c0, c1, contactId1From0, num);
	}

	protected void ack0To2(int num) throws Exception {
		sendAcks(c0, c2, contactId2From0, num);
	}

	protected void ack1To0(int num) throws Exception {
		sendAcks(c1, c0, contactId0From1, num);
	}

	protected void ack2To0(int num) throws Exception {
		sendAcks(c2, c0, contactId0From2, num);
	}

	protected void ack2To1(int num) throws Exception {
		assertNotNull(contactId1From2);
		sendAcks(c2, c1, contactId1From2, num);
	}

	protected void ack1To2(int num) throws Exception {
		assertNotNull(contactId2From1);
		sendAcks(c1, c2, contactId2From1, num);
	}

	protected void removeAllContacts() throws DbException {
		contactManager0.removeContact(contactId1From0);
		contactManager0.removeContact(contactId2From0);
		contactManager1.removeContact(contactId0From1);
		contactManager2.removeContact(contactId0From2);
		assertNotNull(contactId2From1);
		contactManager1.removeContact(contactId2From1);
		assertNotNull(contactId1From2);
		contactManager2.removeContact(contactId1From2);
	}

	protected void setAutoDeleteTimer(BriarIntegrationTestComponent component,
			ContactId contactId, long timer) throws DbException {
		DatabaseComponent db = component.getDatabaseComponent();
		AutoDeleteManager autoDeleteManager = component.getAutoDeleteManager();

		db.transaction(false, txn ->
				autoDeleteManager.setAutoDeleteTimer(txn, contactId, timer));
	}

	protected long getAutoDeleteTimer(BriarIntegrationTestComponent component,
			ContactId contactId, long timestamp) throws DbException {
		DatabaseComponent db = component.getDatabaseComponent();
		AutoDeleteManager autoDeleteManager = component.getAutoDeleteManager();

		return db.transactionWithResult(false,
				txn -> autoDeleteManager.getAutoDeleteTimer(txn, contactId,
						timestamp));
	}

	protected void setMessageNotShared(BriarIntegrationTestComponent component,
			MessageId messageId) throws Exception {
		DatabaseComponent db = component.getDatabaseComponent();

		db.transaction(false, txn -> db.setMessageNotShared(txn, messageId));
	}

	protected void setMessageShared(BriarIntegrationTestComponent component,
			MessageId messageId) throws Exception {
		DatabaseComponent db = component.getDatabaseComponent();

		db.transaction(false, txn -> db.setMessageShared(txn, messageId));
	}

}
