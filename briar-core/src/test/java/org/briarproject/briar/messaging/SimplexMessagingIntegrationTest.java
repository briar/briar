package org.briarproject.briar.messaging;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.Identity;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.event.MessageStateChangedEvent;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.bramble.test.TestTransportConnectionReader;
import org.briarproject.bramble.test.TestTransportConnectionWriter;
import org.briarproject.briar.api.attachment.AttachmentHeader;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.briarproject.briar.api.messaging.PrivateMessage;
import org.briarproject.briar.api.messaging.PrivateMessageFactory;
import org.briarproject.briar.api.messaging.event.AttachmentReceivedEvent;
import org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent;
import org.briarproject.briar.test.BriarTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.briarproject.bramble.api.sync.validation.MessageState.DELIVERED;
import static org.briarproject.bramble.test.TestPluginConfigModule.SIMPLEX_TRANSPORT_ID;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.junit.Assert.assertTrue;

public class SimplexMessagingIntegrationTest extends BriarTestCase {

	private static final int TIMEOUT_MS = 5_000;

	private final File testDir = getTestDirectory();
	private final File aliceDir = new File(testDir, "alice");
	private final File bobDir = new File(testDir, "bob");

	private final SecretKey rootKey = getSecretKey();
	private final long timestamp = System.currentTimeMillis();

	private SimplexMessagingIntegrationTestComponent alice, bob;

	@Before
	public void setUp() {
		assertTrue(testDir.mkdirs());
		alice = DaggerSimplexMessagingIntegrationTestComponent.builder()
				.testDatabaseConfigModule(
						new TestDatabaseConfigModule(aliceDir)).build();
		SimplexMessagingIntegrationTestComponent.Helper
				.injectEagerSingletons(alice);
		bob = DaggerSimplexMessagingIntegrationTestComponent.builder()
				.testDatabaseConfigModule(new TestDatabaseConfigModule(bobDir))
				.build();
		SimplexMessagingIntegrationTestComponent.Helper
				.injectEagerSingletons(bob);
	}

	@Test
	public void testWriteAndRead() throws Exception {
		// Create the identities
		Identity aliceIdentity =
				alice.getIdentityManager().createIdentity("Alice");
		Identity bobIdentity = bob.getIdentityManager().createIdentity("Bob");
		// Set up the devices and get the contact IDs
		ContactId bobId = setUp(alice, aliceIdentity,
				bobIdentity.getLocalAuthor(), true);
		ContactId aliceId = setUp(bob, bobIdentity,
				aliceIdentity.getLocalAuthor(), false);
		// Add a private message listener
		PrivateMessageListener listener = new PrivateMessageListener();
		bob.getEventBus().addListener(listener);
		// Alice sends a private message to Bob
		sendMessage(alice, bobId);
		// Sync Alice's client versions and transport properties
		read(bob, write(alice, bobId), 2);
		// Sync Bob's client versions and transport properties
		read(alice, write(bob, aliceId), 2);
		// Sync the private message and the attachment
		read(bob, write(alice, bobId), 2);
		// Bob should have received the private message
		assertTrue(listener.messageAdded);
		// Bob should have received the attachment
		assertTrue(listener.attachmentAdded);
	}

	private ContactId setUp(SimplexMessagingIntegrationTestComponent device,
			Identity local, Author remote, boolean alice) throws Exception {
		// Add an identity for the user
		IdentityManager identityManager = device.getIdentityManager();
		identityManager.registerIdentity(local);
		// Start the lifecycle manager
		LifecycleManager lifecycleManager = device.getLifecycleManager();
		lifecycleManager.startServices(getSecretKey());
		lifecycleManager.waitForStartup();
		// Add the other user as a contact
		ContactManager contactManager = device.getContactManager();
		return contactManager.addContact(remote, local.getId(), rootKey,
				timestamp, alice, true, true);
	}

	private void sendMessage(SimplexMessagingIntegrationTestComponent device,
			ContactId contactId) throws Exception {
		MessagingManager messagingManager = device.getMessagingManager();
		GroupId groupId = messagingManager.getConversationId(contactId);
		long timestamp = System.currentTimeMillis();
		InputStream in = new ByteArrayInputStream(new byte[] {0, 1, 2, 3});
		AttachmentHeader attachmentHeader = messagingManager.addLocalAttachment(
				groupId, timestamp, "image/png", in);
		PrivateMessageFactory privateMessageFactory =
				device.getPrivateMessageFactory();
		PrivateMessage message = privateMessageFactory.createPrivateMessage(
				groupId, timestamp, "Hi!", singletonList(attachmentHeader), -1);
		messagingManager.addLocalMessage(message);
	}

	@SuppressWarnings("SameParameterValue")
	private void read(SimplexMessagingIntegrationTestComponent device,
			byte[] stream, int deliveries) throws Exception {
		// Listen for message deliveries
		MessageDeliveryListener listener =
				new MessageDeliveryListener(deliveries);
		device.getEventBus().addListener(listener);
		// Read the incoming stream
		ByteArrayInputStream in = new ByteArrayInputStream(stream);
		TestTransportConnectionReader reader =
				new TestTransportConnectionReader(in);
		device.getConnectionManager().manageIncomingConnection(
				SIMPLEX_TRANSPORT_ID, reader);
		// Wait for the messages to be delivered
		assertTrue(listener.delivered.await(TIMEOUT_MS, MILLISECONDS));
		// Clean up the listener
		device.getEventBus().removeListener(listener);
	}

	private byte[] write(SimplexMessagingIntegrationTestComponent device,
			ContactId contactId) throws Exception {
		// Write the outgoing stream
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TestTransportConnectionWriter writer =
				new TestTransportConnectionWriter(out);
		device.getConnectionManager().manageOutgoingConnection(contactId,
				SIMPLEX_TRANSPORT_ID, writer);
		// Wait for the writer to be disposed
		writer.getDisposedLatch().await(TIMEOUT_MS, MILLISECONDS);
		// Return the contents of the stream
		return out.toByteArray();
	}

	private void tearDown(SimplexMessagingIntegrationTestComponent device)
			throws Exception {
		// Stop the lifecycle manager
		LifecycleManager lifecycleManager = device.getLifecycleManager();
		lifecycleManager.stopServices();
		lifecycleManager.waitForShutdown();
	}

	@After
	public void tearDown() throws Exception {
		// Tear down the devices
		tearDown(alice);
		tearDown(bob);
		deleteTestDirectory(testDir);
	}

	@NotNullByDefault
	private static class MessageDeliveryListener implements EventListener {

		private final CountDownLatch delivered;

		private MessageDeliveryListener(int deliveries) {
			delivered = new CountDownLatch(deliveries);
		}

		@Override
		public void eventOccurred(Event e) {
			if (e instanceof MessageStateChangedEvent) {
				MessageStateChangedEvent m = (MessageStateChangedEvent) e;
				if (m.getState().equals(DELIVERED)) delivered.countDown();
			}
		}
	}

	@NotNullByDefault
	private static class PrivateMessageListener implements EventListener {

		private volatile boolean messageAdded = false;
		private volatile boolean attachmentAdded = false;

		@Override
		public void eventOccurred(Event e) {
			if (e instanceof PrivateMessageReceivedEvent) {
				messageAdded = true;
			} else if (e instanceof AttachmentReceivedEvent) {
				attachmentAdded = true;
			}
		}
	}
}
