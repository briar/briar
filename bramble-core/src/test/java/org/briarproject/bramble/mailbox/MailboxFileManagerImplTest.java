package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.connection.ConnectionManager;
import org.briarproject.bramble.api.connection.ConnectionManager.TagController;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.plugin.event.TransportActiveEvent;
import org.briarproject.bramble.api.plugin.simplex.SimplexPlugin;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.sync.OutgoingSessionRecord;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.CaptureArgumentAction;
import org.briarproject.bramble.test.ConsumeArgumentAction;
import org.briarproject.bramble.test.RunAction;
import org.jmock.Expectations;
import org.jmock.lib.action.DoAllAction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.RUNNING;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.STOPPING;
import static org.briarproject.bramble.api.mailbox.MailboxConstants.ID;
import static org.briarproject.bramble.api.plugin.file.FileConstants.PROP_PATH;
import static org.briarproject.bramble.mailbox.MailboxFileManagerImpl.DOWNLOAD_DIR_NAME;
import static org.briarproject.bramble.mailbox.MailboxFileManagerImpl.UPLOAD_DIR_NAME;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getContactId;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MailboxFileManagerImplTest extends BrambleMockTestCase {

	private final Executor ioExecutor = context.mock(Executor.class);
	private final PluginManager pluginManager =
			context.mock(PluginManager.class);
	private final ConnectionManager connectionManager =
			context.mock(ConnectionManager.class);
	private final LifecycleManager lifecycleManager =
			context.mock(LifecycleManager.class);
	private final EventBus eventBus = context.mock(EventBus.class);
	private final SimplexPlugin plugin = context.mock(SimplexPlugin.class);
	private final TransportConnectionReader transportConnectionReader =
			context.mock(TransportConnectionReader.class);
	private final TransportConnectionWriter transportConnectionWriter =
			context.mock(TransportConnectionWriter.class);

	private final ContactId contactId = getContactId();

	private File mailboxDir;
	private MailboxFileManagerImpl manager;

	@Before
	public void setUp() {
		mailboxDir = getTestDirectory();
		manager = new MailboxFileManagerImpl(ioExecutor, pluginManager,
				connectionManager, lifecycleManager, mailboxDir, eventBus);
	}

	@After
	public void tearDown() {
		deleteTestDirectory(mailboxDir);
	}

	@Test
	public void testHandlesOrphanedFilesAtStartup() throws Exception {
		// Create an orphaned upload, left behind at the previous shutdown
		File uploadDir = new File(mailboxDir, UPLOAD_DIR_NAME);
		//noinspection ResultOfMethodCallIgnored
		uploadDir.mkdirs();
		File orphanedUpload = new File(uploadDir, "orphan");
		assertTrue(orphanedUpload.createNewFile());

		// Create an orphaned download, left behind at the previous shutdown
		File downloadDir = new File(mailboxDir, DOWNLOAD_DIR_NAME);
		//noinspection ResultOfMethodCallIgnored
		downloadDir.mkdirs();
		File orphanedDownload = new File(downloadDir, "orphan");
		assertTrue(orphanedDownload.createNewFile());

		TransportProperties props = new TransportProperties();
		props.put(PROP_PATH, orphanedDownload.getAbsolutePath());

		// When the plugin becomes active the orphaned upload should be deleted
		// and the orphaned download should be handled
		context.checking(new Expectations() {{
			oneOf(ioExecutor).execute(with(any(Runnable.class)));
			will(new RunAction());
			oneOf(eventBus).removeListener(manager);
			oneOf(pluginManager).getPlugin(ID);
			will(returnValue(plugin));
			oneOf(plugin).createReader(props);
			will(returnValue(transportConnectionReader));
			oneOf(connectionManager).manageIncomingConnection(with(ID),
					with(any(TransportConnectionReader.class)),
					with(any(TagController.class)));
		}});

		manager.eventOccurred(new TransportActiveEvent(ID));

		assertFalse(orphanedUpload.exists());
	}

	@Test
	public void testDeletesDownloadedFileWhenReadSucceeds() throws Exception {
		expectCheckForOrphans();
		manager.eventOccurred(new TransportActiveEvent(ID));

		File f = manager.createTempFileForDownload();
		AtomicReference<TransportConnectionReader> reader =
				new AtomicReference<>(null);
		AtomicReference<TagController> controller = new AtomicReference<>(null);

		expectPassDownloadedFileToConnectionManager(f, reader, controller);
		manager.handleDownloadedFile(f);

		// The read is successful, so the tag controller should allow the tag
		// to be marked as read and the reader should delete the file
		context.checking(new Expectations() {{
			oneOf(transportConnectionReader).dispose(false, true);
		}});

		assertTrue(controller.get().shouldMarkTagAsRecognised(false));
		reader.get().dispose(false, true);
		assertFalse(f.exists());
	}

	@Test
	public void testDeletesDownloadedFileWhenTagIsNotRecognised()
			throws Exception {
		testDeletesDownloadedFile(false, RUNNING, false);
	}

	@Test
	public void testDeletesDownloadedFileWhenReadFails() throws Exception {
		testDeletesDownloadedFile(true, RUNNING, false);
	}

	@Test
	public void testDoesNotDeleteDownloadedFileWhenTagIsNotRecognisedAtShutdown()
			throws Exception {
		testDeletesDownloadedFile(false, STOPPING, true);
	}

	@Test
	public void testDoesNotDeleteDownloadedFileWhenReadFailsAtShutdown()
			throws Exception {
		testDeletesDownloadedFile(true, STOPPING, true);
	}

	@Test(expected = IOException.class)
	public void testThrowsExceptionIfPluginFailsToCreateWriter()
			throws Exception {
		OutgoingSessionRecord sessionRecord = new OutgoingSessionRecord();

		expectCheckForOrphans();
		manager.eventOccurred(new TransportActiveEvent(ID));

		context.checking(new Expectations() {{
			oneOf(pluginManager).getPlugin(ID);
			will(returnValue(plugin));
			oneOf(plugin).createWriter(with(any(TransportProperties.class)));
			will(returnValue(null));
		}});

		manager.createAndWriteTempFileForUpload(contactId, sessionRecord);
	}

	@Test(expected = IOException.class)
	public void testThrowsExceptionIfSessionFailsWithException()
			throws Exception {
		OutgoingSessionRecord sessionRecord = new OutgoingSessionRecord();

		expectCheckForOrphans();
		manager.eventOccurred(new TransportActiveEvent(ID));

		context.checking(new Expectations() {{
			oneOf(pluginManager).getPlugin(ID);
			will(returnValue(plugin));
			oneOf(plugin).createWriter(with(any(TransportProperties.class)));
			will(returnValue(transportConnectionWriter));
			oneOf(transportConnectionWriter).dispose(true);
			oneOf(connectionManager).manageOutgoingConnection(with(contactId),
					with(ID), with(any(TransportConnectionWriter.class)),
					with(sessionRecord));
			// The session fails with an exception. We need to use an action
			// for this, as createAndWriteTempFileForUpload() waits for it to
			// happen before returning
			will(new ConsumeArgumentAction<>(TransportConnectionWriter.class, 2,
					writer -> {
						try {
							writer.dispose(true);
						} catch (IOException e) {
							fail();
						}
					}
			));
		}});

		manager.createAndWriteTempFileForUpload(contactId, sessionRecord);
	}

	@Test
	public void testReturnsFileIfSessionSucceeds() throws Exception {
		OutgoingSessionRecord sessionRecord = new OutgoingSessionRecord();

		expectCheckForOrphans();
		manager.eventOccurred(new TransportActiveEvent(ID));

		context.checking(new Expectations() {{
			oneOf(pluginManager).getPlugin(ID);
			will(returnValue(plugin));
			oneOf(plugin).createWriter(with(any(TransportProperties.class)));
			will(returnValue(transportConnectionWriter));
			oneOf(transportConnectionWriter).dispose(false);
			oneOf(connectionManager).manageOutgoingConnection(with(contactId),
					with(ID), with(any(TransportConnectionWriter.class)),
					with(sessionRecord));
			// The session succeeds. We need to use an action for this, as
			// createAndWriteTempFileForUpload() waits for it to happen before
			// returning
			will(new ConsumeArgumentAction<>(TransportConnectionWriter.class, 2,
					writer -> {
						try {
							writer.dispose(false);
						} catch (IOException e) {
							fail();
						}
					}
			));
		}});

		File f = manager.createAndWriteTempFileForUpload(contactId,
				sessionRecord);
		assertTrue(f.exists());
	}

	private void testDeletesDownloadedFile(boolean recognised,
			LifecycleState state, boolean fileExists) throws Exception {
		expectCheckForOrphans();
		manager.eventOccurred(new TransportActiveEvent(ID));

		File f = manager.createTempFileForDownload();
		AtomicReference<TransportConnectionReader> reader =
				new AtomicReference<>(null);
		AtomicReference<TagController> controller = new AtomicReference<>(null);

		expectPassDownloadedFileToConnectionManager(f, reader, controller);
		manager.handleDownloadedFile(f);

		context.checking(new Expectations() {{
			oneOf(transportConnectionReader).dispose(true, recognised);
			oneOf(lifecycleManager).getLifecycleState();
			will(returnValue(state));
		}});

		reader.get().dispose(true, recognised);
		assertEquals(fileExists, f.exists());
	}

	private void expectCheckForOrphans() {
		context.checking(new Expectations() {{
			oneOf(ioExecutor).execute(with(any(Runnable.class)));
			will(new RunAction());
			oneOf(eventBus).removeListener(manager);
		}});
	}

	private void expectPassDownloadedFileToConnectionManager(File f,
			AtomicReference<TransportConnectionReader> reader,
			AtomicReference<TagController> controller) {
		TransportProperties props = new TransportProperties();
		props.put(PROP_PATH, f.getAbsolutePath());

		context.checking(new Expectations() {{
			oneOf(pluginManager).getPlugin(ID);
			will(returnValue(plugin));
			oneOf(plugin).createReader(props);
			will(returnValue(transportConnectionReader));
			oneOf(connectionManager).manageIncomingConnection(with(ID),
					with(any(TransportConnectionReader.class)),
					with(any(TagController.class)));
			will(new DoAllAction(
					new CaptureArgumentAction<>(reader,
							TransportConnectionReader.class, 1),
					new CaptureArgumentAction<>(controller,
							TagController.class, 2)
			));
		}});
	}
}
