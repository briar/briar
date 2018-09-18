package org.briarproject.briar.android;

import android.support.test.espresso.intent.rule.IntentsTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiSelector;
import android.util.Log;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.SyncSession;
import org.briarproject.bramble.api.sync.SyncSessionFactory;
import org.briarproject.bramble.api.transport.KeyManager;
import org.briarproject.bramble.api.transport.StreamContext;
import org.briarproject.bramble.api.transport.StreamReaderFactory;
import org.briarproject.bramble.api.transport.StreamWriter;
import org.briarproject.bramble.api.transport.StreamWriterFactory;
import org.briarproject.bramble.contact.ContactModule;
import org.briarproject.bramble.identity.IdentityModule;
import org.briarproject.bramble.lifecycle.LifecycleModule;
import org.briarproject.bramble.sync.SyncModule;
import org.briarproject.bramble.system.SystemModule;
import org.briarproject.bramble.test.TestDatabaseModule;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.bramble.transport.TransportModule;
import org.briarproject.bramble.versioning.VersioningModule;
import org.briarproject.briar.R;
import org.briarproject.briar.android.login.OpenDatabaseActivity;
import org.briarproject.briar.android.login.SetupActivity;
import org.briarproject.briar.android.navdrawer.NavDrawerActivity;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.briarproject.briar.api.messaging.PrivateMessage;
import org.briarproject.briar.api.messaging.PrivateMessageFactory;
import org.briarproject.briar.messaging.MessagingModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

import static android.content.Context.MODE_PRIVATE;
import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static android.support.test.InstrumentationRegistry.getTargetContext;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static android.support.test.espresso.intent.Intents.intended;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.isRoot;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static android.support.test.runner.lifecycle.Stage.PAUSED;
import static org.briarproject.bramble.api.plugin.LanTcpConstants.ID;
import static org.briarproject.bramble.api.transport.TransportConstants.TAG_LENGTH;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.briar.android.TestPluginConfigModule.MAX_LATENCY;
import static org.briarproject.briar.android.TestPluginConfigModule.TRANSPORT_ID;
import static org.briarproject.briar.android.ViewActions.waitForActivity;
import static org.briarproject.briar.android.ViewActions.waitUntilMatches;
import static org.briarproject.briar.android.util.UiUtils.needsDozeWhitelisting;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class SetupDataTest extends ScreenshotTest {

	@Rule
	public IntentsTestRule<SetupActivity> testRule =
			new IntentsTestRule<SetupActivity>(SetupActivity.class) {
				@Override
				protected void beforeActivityLaunched() {
					super.beforeActivityLaunched();
					accountManager.deleteAccount();
				}
			};

	private FakeDataTestComponent bob;
	private final File testDir =
			getTargetContext().getDir("test", MODE_PRIVATE);
	private final File bobDir = new File(testDir, "bob");
	private final SecretKey master = getSecretKey();

	@Override
	protected void inject(BriarUiTestComponent component) {
		component.inject(this);
	}

	@Before
	public void setUp() {
		Log.e("TEST", testDir.getAbsolutePath());
		Log.e("TEST", "exists: " + testDir.exists());
		assertTrue(testDir.mkdirs());
		bob = DaggerFakeDataTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(bobDir)).build();
		injectEagerSingletons(bob);
		Log.e("TEST", "built bob");
	}

	@After
	public void tearDown() throws Exception {
		// Stop the lifecycle manager
		LifecycleManager lifecycleManager = bob.getLifecycleManager();
		lifecycleManager.stopServices();
		lifecycleManager.waitForShutdown();

		TestUtils.deleteTestDirectory(testDir);
	}

	private static void injectEagerSingletons(FakeDataTestComponent component) {
		component.inject(new ContactModule.EagerSingletons());
		component.inject(new IdentityModule.EagerSingletons());
		component.inject(new LifecycleModule.EagerSingletons());
		component.inject(new MessagingModule.EagerSingletons());
		component.inject(new SyncModule.EagerSingletons());
		component.inject(new SystemModule.EagerSingletons());
		component.inject(new TransportModule.EagerSingletons());
		component.inject(new VersioningModule.EagerSingletons());
	}

	@Test
	public void createAccount() throws Exception {
		// Enter username
		onView(withText(R.string.setup_title))
				.check(matches(isDisplayed()));
		onView(withId(R.id.nickname_entry))
				.check(matches(isDisplayed()))
				.perform(typeText(USERNAME));
		onView(withId(R.id.nickname_entry))
				.perform(waitUntilMatches(withText(USERNAME)));

		screenshot("manual_create_account", testRule.getActivity());

		onView(withId(R.id.next))
				.check(matches(isDisplayed()))
				.perform(click());

		// Enter password
		onView(withId(R.id.password_entry))
				.check(matches(isDisplayed()))
				.perform(typeText(PASSWORD));
		onView(withId(R.id.password_confirm))
				.check(matches(isDisplayed()))
				.perform(typeText(PASSWORD));
		onView(withId(R.id.next))
				.check(matches(isDisplayed()))
				.perform(click());

		// White-list Doze if needed
		if (needsDozeWhitelisting(getTargetContext())) {
			onView(withText(R.string.setup_doze_button))
					.check(matches(isDisplayed()))
					.perform(click());
			UiDevice device = UiDevice.getInstance(getInstrumentation());
			UiObject allowButton = device.findObject(
					new UiSelector().className("android.widget.Button")
							.index(1));
			allowButton.click();
			onView(withId(R.id.next))
					.check(matches(isDisplayed()))
					.perform(click());
		}

		// wait for OpenDatabaseActivity to show up
		onView(isRoot())
				.perform(waitForActivity(testRule.getActivity(), PAUSED));
		intended(hasComponent(OpenDatabaseActivity.class.getName()));

		assertTrue(accountManager.hasDatabaseKey());

		// WIP below

		// wait for OpenDatabaseActivity to go away
		onView(isRoot())
				.perform(waitUntilMatches(
						allOf(withId(R.id.progressBar), not(isDisplayed()))));
		lifecycleManager.waitForStartup();
		intended(hasComponent(NavDrawerActivity.class.getName()));

		// close expiry warning
		onView(withId(R.id.expiryWarningClose))
				.check(matches(isDisplayed()));
		onView(withId(R.id.expiryWarningClose))
				.perform(click());

		LocalAuthor aliceAuthor = alice.identityManager().getLocalAuthor();

		LocalAuthor bobAuthor = bob.getIdentityManager().createLocalAuthor(
				getTargetContext().getString(R.string.screenshot_bob));
		bob.getIdentityManager().registerLocalAuthor(bobAuthor);
		// Start the lifecycle manager
		bob.getLifecycleManager().startServices(getSecretKey());
		bob.getLifecycleManager().waitForStartup();
		long timestamp = clock.currentTimeMillis();
		// Bob adds Alice as a contact
		ContactId aliceContactId = bob.getContactManager()
				.addContact(aliceAuthor, bobAuthor.getId(), master,
						timestamp, true, true, true);
		// Alice adds Bob as a contact
		ContactId bobContactId = alice.contactManager()
				.addContact(bobAuthor, aliceAuthor.getId(), master,
						timestamp, false, true, true);

		// TODO figure out how many messages
		read(alice, bobContactId, write(bob, aliceContactId));
		read(bob, aliceContactId, write(alice, bobContactId));
		read(alice, bobContactId, write(bob, aliceContactId));
		read(bob, aliceContactId, write(alice, bobContactId));
		read(alice, bobContactId, write(bob, aliceContactId));
		read(bob, aliceContactId, write(alice, bobContactId));
		read(alice, bobContactId, write(bob, aliceContactId));
		read(bob, aliceContactId, write(alice, bobContactId));
		read(alice, bobContactId, write(bob, aliceContactId));
		read(bob, aliceContactId, write(alice, bobContactId));
		read(alice, bobContactId, write(bob, aliceContactId));
		read(bob, aliceContactId, write(alice, bobContactId));
		read(alice, bobContactId, write(bob, aliceContactId));

		sendMessage(bob, aliceContactId);
		read(alice, bobContactId, write(bob, aliceContactId));

		onView(isRoot())
				.perform(waitUntilMatches(withText(bobAuthor.getName())));
		onView(withId(R.id.recyclerView))
				.perform(actionOnItemAtPosition(0, click()));
		onView(isRoot())
				.perform(waitUntilMatches(withText(R.string.screenshot_message_2)));

		assertEquals(1,
				alice.conversationManager().getGroupCount(bobContactId).getMsgCount());

		Thread.sleep(5000);
	}

	private void sendMessage(FakeDataTestComponent device, ContactId contactId)
			throws Exception {
		// Send Bob a message
		MessagingManager messagingManager = device.getMessagingManager();
		GroupId groupId = messagingManager.getConversationId(contactId);
		PrivateMessageFactory privateMessageFactory =
				device.getPrivateMessageFactory();
		PrivateMessage message = privateMessageFactory.createPrivateMessage(
				groupId, getMinutesAgo(3),
				getTargetContext().getString(R.string.screenshot_message_2));
		messagingManager.addLocalMessage(message);
	}

	private void read(FakeDataTestComponent device,
			ContactId contactId, byte[] stream) throws Exception {
		// Read and recognise the tag
		ByteArrayInputStream in = new ByteArrayInputStream(stream);
		byte[] tag = new byte[TAG_LENGTH];
		int read = in.read(tag);
		assertEquals(tag.length, read);
		KeyManager keyManager = device.getKeyManager();
		StreamContext ctx = keyManager.getStreamContext(TRANSPORT_ID, tag);
		assertNotNull(ctx);
		// Create a stream reader
		StreamReaderFactory streamReaderFactory =
				device.getStreamReaderFactory();
		InputStream streamReader = streamReaderFactory.createStreamReader(
				in, ctx);
		// Create an incoming sync session
		SyncSessionFactory syncSessionFactory = device.getSyncSessionFactory();
		SyncSession session = syncSessionFactory.createIncomingSession(
				contactId, streamReader);
		// Read whatever needs to be read
		session.run();
		streamReader.close();
	}

	private byte[] write(FakeDataTestComponent device,
			ContactId contactId) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// Get a stream context
		KeyManager keyManager = device.getKeyManager();
		StreamContext ctx = keyManager.getStreamContext(contactId,
				TRANSPORT_ID);
		assertNotNull(ctx);
		// Create a stream writer
		StreamWriterFactory streamWriterFactory =
				device.getStreamWriterFactory();
		StreamWriter streamWriter =
				streamWriterFactory.createStreamWriter(out, ctx);
		// Create an outgoing sync session
		SyncSessionFactory syncSessionFactory = device.getSyncSessionFactory();
		SyncSession session = syncSessionFactory.createSimplexOutgoingSession(
				contactId, MAX_LATENCY, streamWriter);
		// Write whatever needs to be written
		session.run();
		streamWriter.sendEndOfStream();
		// Return the contents of the stream
		return out.toByteArray();
	}

	@Deprecated
	private void createTestDataExceptions()
			throws DbException, FormatException {
		String bobName =
				getTargetContext().getString(R.string.screenshot_bob);
		Contact bob = testDataCreator.addContact(bobName);

		String bobHi = getTargetContext()
				.getString(R.string.screenshot_message_1);
		long bobTime = getMinutesAgo(2);
		testDataCreator.addPrivateMessage(bob, bobHi, bobTime, true);

		String aliceHi = getTargetContext()
				.getString(R.string.screenshot_message_2);
		long aliceTime = getMinutesAgo(1);
		testDataCreator.addPrivateMessage(bob, aliceHi, aliceTime, false);

		String bobHi2 = getTargetContext()
				.getString(R.string.screenshot_message_3);
		long bobTime2 = getMinutesAgo(0);
		testDataCreator.addPrivateMessage(bob, bobHi2, bobTime2, true);

		connectionRegistry.registerConnection(bob.getId(), ID, true);
	}

}
