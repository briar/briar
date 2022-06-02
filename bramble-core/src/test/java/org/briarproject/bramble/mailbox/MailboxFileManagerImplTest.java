package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.connection.ConnectionManager;
import org.briarproject.bramble.api.connection.ConnectionManager.TagController;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.plugin.event.TransportActiveEvent;
import org.briarproject.bramble.api.plugin.simplex.SimplexPlugin;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.CaptureArgumentAction;
import org.briarproject.bramble.test.RunAction;
import org.jmock.Expectations;
import org.jmock.lib.action.DoAllAction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.RUNNING;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.STOPPING;
import static org.briarproject.bramble.api.mailbox.MailboxConstants.ID;
import static org.briarproject.bramble.api.plugin.file.FileConstants.PROP_PATH;
import static org.briarproject.bramble.mailbox.MailboxFileManagerImpl.DOWNLOAD_DIR_NAME;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
		// Create an orphaned file, left behind at the previous shutdown
		File downloadDir = new File(mailboxDir, DOWNLOAD_DIR_NAME);
		//noinspection ResultOfMethodCallIgnored
		downloadDir.mkdirs();
		File orphan = new File(downloadDir, "orphan");
		assertTrue(orphan.createNewFile());

		TransportProperties props = new TransportProperties();
		props.put(PROP_PATH, orphan.getAbsolutePath());

		// When the plugin becomes active the orphaned file should be handled
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
	}

	@Test
	public void testDeletesFileWhenReadSucceeds() throws Exception {
		expectCheckForOrphans();
		manager.eventOccurred(new TransportActiveEvent(ID));

		File f = manager.createTempFileForDownload();
		AtomicReference<TransportConnectionReader> reader =
				new AtomicReference<>(null);
		AtomicReference<TagController> controller = new AtomicReference<>(null);

		expectPassFileToConnectionManager(f, reader, controller);
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
	public void testDeletesFileWhenTagIsNotRecognised() throws Exception {
		testDeletesFile(false, RUNNING, false);
	}

	@Test
	public void testDeletesFileWhenReadFails() throws Exception {
		testDeletesFile(true, RUNNING, false);
	}

	@Test
	public void testDoesNotDeleteFileWhenTagIsNotRecognisedAtShutdown()
			throws Exception {
		testDeletesFile(false, STOPPING, true);
	}

	@Test
	public void testDoesNotDeleteFileWhenReadFailsAtShutdown()
			throws Exception {
		testDeletesFile(true, STOPPING, true);
	}

	private void testDeletesFile(boolean recognised, LifecycleState state,
			boolean fileExists) throws Exception {
		expectCheckForOrphans();
		manager.eventOccurred(new TransportActiveEvent(ID));

		File f = manager.createTempFileForDownload();
		AtomicReference<TransportConnectionReader> reader =
				new AtomicReference<>(null);
		AtomicReference<TagController> controller = new AtomicReference<>(null);

		expectPassFileToConnectionManager(f, reader, controller);
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

	private void expectPassFileToConnectionManager(File f,
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
