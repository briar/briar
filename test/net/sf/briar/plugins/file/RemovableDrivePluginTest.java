package net.sf.briar.plugins.file;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.plugins.BatchTransportCallback;
import net.sf.briar.api.transport.BatchTransportWriter;
import net.sf.briar.api.transport.TransportConstants;
import net.sf.briar.plugins.ImmediateExecutor;
import net.sf.briar.plugins.file.RemovableDriveMonitor.Callback;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RemovableDrivePluginTest extends TestCase {

	private final File testDir = TestUtils.getTestDirectory();
	private final ContactId contactId = new ContactId(0);

	private Map<String, String> localProperties = null;
	private Map<ContactId, Map<String, String>> remoteProperties = null;
	private Map<String, String> config = null;

	@Before
	public void setUp() {
		localProperties = new TreeMap<String, String>();
		remoteProperties = new HashMap<ContactId, Map<String, String>>();
		remoteProperties.put(contactId, new TreeMap<String, String>());
		config = new TreeMap<String, String>();
		testDir.mkdirs();
	}

	@Test
	public void testGetId() {
		Mockery context = new Mockery();
		final Executor executor = context.mock(Executor.class);
		final BatchTransportCallback callback =
			context.mock(BatchTransportCallback.class);
		final RemovableDriveFinder finder =
			context.mock(RemovableDriveFinder.class);
		final RemovableDriveMonitor monitor =
			context.mock(RemovableDriveMonitor.class);

		RemovableDrivePlugin plugin = new RemovableDrivePlugin(executor,
				callback, finder, monitor);

		assertEquals(RemovableDrivePlugin.TRANSPORT_ID,
				plugin.getId().getInt());

		context.assertIsSatisfied();
	}

	@Test
	public void testWriterIsNullIfNoDrivesAreFound() throws Exception {
		final List<File> drives = Collections.emptyList();

		Mockery context = new Mockery();
		final Executor executor = context.mock(Executor.class);
		final BatchTransportCallback callback =
			context.mock(BatchTransportCallback.class);
		final RemovableDriveFinder finder =
			context.mock(RemovableDriveFinder.class);
		final RemovableDriveMonitor monitor =
			context.mock(RemovableDriveMonitor.class);

		context.checking(new Expectations() {{
			oneOf(monitor).start(with(any(Callback.class)));
			oneOf(finder).findRemovableDrives();
			will(returnValue(drives));
		}});

		RemovableDrivePlugin plugin = new RemovableDrivePlugin(executor,
				callback, finder, monitor);
		plugin.start(localProperties, remoteProperties, config);

		assertNull(plugin.createWriter(contactId));

		context.assertIsSatisfied();
	}

	@Test
	public void testWriterIsNullIfNoDriveIsChosen() throws Exception {
		final File drive1 = new File(testDir, "1");
		final File drive2 = new File(testDir, "2");
		final List<File> drives = new ArrayList<File>();
		drives.add(drive1);
		drives.add(drive2);

		Mockery context = new Mockery();
		final Executor executor = context.mock(Executor.class);
		final BatchTransportCallback callback =
			context.mock(BatchTransportCallback.class);
		final RemovableDriveFinder finder =
			context.mock(RemovableDriveFinder.class);
		final RemovableDriveMonitor monitor =
			context.mock(RemovableDriveMonitor.class);

		context.checking(new Expectations() {{
			oneOf(monitor).start(with(any(Callback.class)));
			oneOf(finder).findRemovableDrives();
			will(returnValue(drives));
			oneOf(callback).showChoice(with(any(String[].class)),
					with(any(String.class)));
			will(returnValue(-1)); // The user cancelled the choice
		}});

		RemovableDrivePlugin plugin = new RemovableDrivePlugin(executor,
				callback, finder, monitor);
		plugin.start(localProperties, remoteProperties, config);

		assertNull(plugin.createWriter(contactId));
		File[] files = drive1.listFiles();
		assertTrue(files == null || files.length == 0);

		context.assertIsSatisfied();
	}

	@Test
	public void testWriterIsNullIfOutputDirDoesNotExist() throws Exception {
		final File drive1 = new File(testDir, "1");
		final File drive2 = new File(testDir, "2");
		final List<File> drives = new ArrayList<File>();
		drives.add(drive1);
		drives.add(drive2);

		Mockery context = new Mockery();
		final Executor executor = context.mock(Executor.class);
		final BatchTransportCallback callback =
			context.mock(BatchTransportCallback.class);
		final RemovableDriveFinder finder =
			context.mock(RemovableDriveFinder.class);
		final RemovableDriveMonitor monitor =
			context.mock(RemovableDriveMonitor.class);

		context.checking(new Expectations() {{
			oneOf(monitor).start(with(any(Callback.class)));
			oneOf(finder).findRemovableDrives();
			will(returnValue(drives));
			oneOf(callback).showChoice(with(any(String[].class)),
					with(any(String.class)));
			will(returnValue(0)); // The user chose drive1 but it doesn't exist
		}});

		RemovableDrivePlugin plugin = new RemovableDrivePlugin(executor,
				callback, finder, monitor);
		plugin.start(localProperties, remoteProperties, config);

		assertNull(plugin.createWriter(contactId));
		File[] files = drive1.listFiles();
		assertTrue(files == null || files.length == 0);

		context.assertIsSatisfied();
	}

	@Test
	public void testWriterIsNullIfOutputDirIsAFile() throws Exception {
		final File drive1 = new File(testDir, "1");
		final File drive2 = new File(testDir, "2");
		final List<File> drives = new ArrayList<File>();
		drives.add(drive1);
		drives.add(drive2);
		// Create drive1 as a file rather than a directory
		assertTrue(drive1.createNewFile());

		Mockery context = new Mockery();
		final Executor executor = context.mock(Executor.class);
		final BatchTransportCallback callback =
			context.mock(BatchTransportCallback.class);
		final RemovableDriveFinder finder =
			context.mock(RemovableDriveFinder.class);
		final RemovableDriveMonitor monitor =
			context.mock(RemovableDriveMonitor.class);

		context.checking(new Expectations() {{
			oneOf(monitor).start(with(any(Callback.class)));
			oneOf(finder).findRemovableDrives();
			will(returnValue(drives));
			oneOf(callback).showChoice(with(any(String[].class)),
					with(any(String.class)));
			will(returnValue(0)); // The user chose drive1 but it's not a dir
		}});

		RemovableDrivePlugin plugin = new RemovableDrivePlugin(executor,
				callback, finder, monitor);
		plugin.start(localProperties, remoteProperties, config);

		assertNull(plugin.createWriter(contactId));
		File[] files = drive1.listFiles();
		assertTrue(files == null || files.length == 0);

		context.assertIsSatisfied();
	}

	@Test
	public void testWriterIsNotNullIfOutputDirIsADir() throws Exception {
		final File drive1 = new File(testDir, "1");
		final File drive2 = new File(testDir, "2");
		final List<File> drives = new ArrayList<File>();
		drives.add(drive1);
		drives.add(drive2);
		// Create drive1 as a directory
		assertTrue(drive1.mkdir());

		Mockery context = new Mockery();
		final Executor executor = context.mock(Executor.class);
		final BatchTransportCallback callback =
			context.mock(BatchTransportCallback.class);
		final RemovableDriveFinder finder =
			context.mock(RemovableDriveFinder.class);
		final RemovableDriveMonitor monitor =
			context.mock(RemovableDriveMonitor.class);

		context.checking(new Expectations() {{
			oneOf(monitor).start(with(any(Callback.class)));
			oneOf(finder).findRemovableDrives();
			will(returnValue(drives));
			oneOf(callback).showChoice(with(any(String[].class)),
					with(any(String.class)));
			will(returnValue(0)); // The user chose drive1
		}});

		RemovableDrivePlugin plugin = new RemovableDrivePlugin(executor,
				callback, finder, monitor);
		plugin.start(localProperties, remoteProperties, config);

		assertNotNull(plugin.createWriter(contactId));
		// The output file should exist and should be empty
		File[] files = drive1.listFiles();
		assertNotNull(files);
		assertEquals(1, files.length);
		assertEquals(0L, files[0].length());

		context.assertIsSatisfied();
	}

	@Test
	public void testWritingToWriter() throws Exception {
		final File drive1 = new File(testDir, "1");
		final File drive2 = new File(testDir, "2");
		final List<File> drives = new ArrayList<File>();
		drives.add(drive1);
		drives.add(drive2);
		// Create drive1 as a directory
		assertTrue(drive1.mkdir());

		Mockery context = new Mockery();
		final Executor executor = context.mock(Executor.class);
		final BatchTransportCallback callback =
			context.mock(BatchTransportCallback.class);
		final RemovableDriveFinder finder =
			context.mock(RemovableDriveFinder.class);
		final RemovableDriveMonitor monitor =
			context.mock(RemovableDriveMonitor.class);

		context.checking(new Expectations() {{
			oneOf(monitor).start(with(any(Callback.class)));
			oneOf(finder).findRemovableDrives();
			will(returnValue(drives));
			oneOf(callback).showChoice(with(any(String[].class)),
					with(any(String.class)));
			will(returnValue(0)); // The user chose drive1
			oneOf(callback).showMessage(with(any(String.class)));
		}});

		RemovableDrivePlugin plugin = new RemovableDrivePlugin(executor,
				callback, finder, monitor);
		plugin.start(localProperties, remoteProperties, config);

		BatchTransportWriter writer = plugin.createWriter(contactId);
		assertNotNull(writer);
		// The output file should exist and should be empty
		File[] files = drive1.listFiles();
		assertNotNull(files);
		assertEquals(1, files.length);
		assertEquals(0L, files[0].length());
		// Writing to the output stream should increase the size of the file
		OutputStream out = writer.getOutputStream();
		out.write(new byte[123]);
		out.flush();
		out.close();
		assertEquals(123L, files[0].length());
		// Disposing of the writer should delete the file
		writer.dispose(true);
		files = drive1.listFiles();
		assertTrue(files == null || files.length == 0);

		context.assertIsSatisfied();
	}

	@Test
	public void testEmptyDriveIsIgnored() throws Exception {
		Mockery context = new Mockery();
		final Executor executor = context.mock(Executor.class);
		final BatchTransportCallback callback =
			context.mock(BatchTransportCallback.class);
		final RemovableDriveFinder finder =
			context.mock(RemovableDriveFinder.class);
		final RemovableDriveMonitor monitor =
			context.mock(RemovableDriveMonitor.class);

		context.checking(new Expectations() {{
			oneOf(monitor).start(with(any(Callback.class)));
		}});

		RemovableDrivePlugin plugin = new RemovableDrivePlugin(executor,
				callback, finder, monitor);
		plugin.start(localProperties, remoteProperties, config);

		plugin.driveInserted(testDir);

		context.assertIsSatisfied();
	}

	@Test
	public void testFilenames() {
		Mockery context = new Mockery();
		final Executor executor = context.mock(Executor.class);
		final BatchTransportCallback callback =
			context.mock(BatchTransportCallback.class);
		final RemovableDriveFinder finder =
			context.mock(RemovableDriveFinder.class);
		final RemovableDriveMonitor monitor =
			context.mock(RemovableDriveMonitor.class);

		RemovableDrivePlugin plugin = new RemovableDrivePlugin(executor,
				callback, finder, monitor);

		assertFalse(plugin.isPossibleConnectionFilename("abcdefg.dat"));
		assertFalse(plugin.isPossibleConnectionFilename("abcdefghi.dat"));
		assertFalse(plugin.isPossibleConnectionFilename("abcdefgh_dat"));
		assertFalse(plugin.isPossibleConnectionFilename("abcdefgh.rat"));
		assertTrue(plugin.isPossibleConnectionFilename("abcdefgh.dat"));
		assertTrue(plugin.isPossibleConnectionFilename("ABCDEFGH.DAT"));

		context.assertIsSatisfied();
	}

	@Test
	public void testSmallFileIsIgnored() throws Exception {
		Mockery context = new Mockery();
		final BatchTransportCallback callback =
			context.mock(BatchTransportCallback.class);
		final RemovableDriveFinder finder =
			context.mock(RemovableDriveFinder.class);
		final RemovableDriveMonitor monitor =
			context.mock(RemovableDriveMonitor.class);

		context.checking(new Expectations() {{
			oneOf(monitor).start(with(any(Callback.class)));
		}});

		RemovableDrivePlugin plugin = new RemovableDrivePlugin(
				new ImmediateExecutor(), callback, finder, monitor);
		plugin.start(localProperties, remoteProperties, config);

		File f = new File(testDir, "abcdefgh.dat");
		OutputStream out = new FileOutputStream(f);
		out.write(new byte[TransportConstants.MIN_CONNECTION_LENGTH - 1]);
		out.flush();
		out.close();
		assertEquals(TransportConstants.MIN_CONNECTION_LENGTH - 1, f.length());
		plugin.driveInserted(testDir);

		context.assertIsSatisfied();
	}

	@Test
	public void testReaderIsCreated() throws Exception {
		Mockery context = new Mockery();
		final BatchTransportCallback callback =
			context.mock(BatchTransportCallback.class);
		final RemovableDriveFinder finder =
			context.mock(RemovableDriveFinder.class);
		final RemovableDriveMonitor monitor =
			context.mock(RemovableDriveMonitor.class);

		context.checking(new Expectations() {{
			oneOf(monitor).start(with(any(Callback.class)));
			oneOf(callback).readerCreated(with(any(FileTransportReader.class)));
		}});

		RemovableDrivePlugin plugin = new RemovableDrivePlugin(
				new ImmediateExecutor(), callback, finder, monitor);
		plugin.start(localProperties, remoteProperties, config);

		File f = new File(testDir, "abcdefgh.dat");
		OutputStream out = new FileOutputStream(f);
		out.write(new byte[TransportConstants.MIN_CONNECTION_LENGTH]);
		out.flush();
		out.close();
		assertEquals(TransportConstants.MIN_CONNECTION_LENGTH, f.length());
		plugin.driveInserted(testDir);

		context.assertIsSatisfied();
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}
}
