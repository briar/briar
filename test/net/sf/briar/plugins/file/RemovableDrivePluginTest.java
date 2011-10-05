package net.sf.briar.plugins.file;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.transport.ConnectionRecogniser;
import net.sf.briar.api.transport.batch.BatchTransportCallback;
import net.sf.briar.api.transport.batch.BatchTransportWriter;
import net.sf.briar.plugins.file.RemovableDriveMonitor.Callback;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RemovableDrivePluginTest extends TestCase {

	private final File testDir = TestUtils.getTestDirectory();
	private final ContactId contactId = new ContactId(0);

	@Before
	public void setUp() {
		testDir.mkdirs();
	}

	@Test
	public void testGetId() {
		Mockery context = new Mockery();
		final ConnectionRecogniser recogniser =
			context.mock(ConnectionRecogniser.class);
		final RemovableDriveFinder finder =
			context.mock(RemovableDriveFinder.class);
		final RemovableDriveMonitor monitor =
			context.mock(RemovableDriveMonitor.class);
		RemovableDrivePlugin plugin = new RemovableDrivePlugin(recogniser,
				finder, monitor);

		assertEquals(RemovableDrivePlugin.TRANSPORT_ID,
				plugin.getId().getInt());

		context.assertIsSatisfied();
	}

	@Test
	public void testWriterIsNullIfNoDrivesAreFound() throws Exception {
		final List<File> drives = Collections.emptyList();

		Mockery context = new Mockery();
		final ConnectionRecogniser recogniser =
			context.mock(ConnectionRecogniser.class);
		final RemovableDriveFinder finder =
			context.mock(RemovableDriveFinder.class);
		final RemovableDriveMonitor monitor =
			context.mock(RemovableDriveMonitor.class);
		final BatchTransportCallback callback =
			context.mock(BatchTransportCallback.class);

		context.checking(new Expectations() {{
			oneOf(monitor).start(with(any(Callback.class)));
			oneOf(finder).findRemovableDrives();
			will(returnValue(drives));
		}});

		RemovableDrivePlugin plugin = new RemovableDrivePlugin(recogniser,
				finder, monitor);
		plugin.start(null, null, null, callback);

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
		final ConnectionRecogniser recogniser =
			context.mock(ConnectionRecogniser.class);
		final RemovableDriveFinder finder =
			context.mock(RemovableDriveFinder.class);
		final RemovableDriveMonitor monitor =
			context.mock(RemovableDriveMonitor.class);
		final BatchTransportCallback callback =
			context.mock(BatchTransportCallback.class);

		context.checking(new Expectations() {{
			oneOf(monitor).start(with(any(Callback.class)));
			oneOf(finder).findRemovableDrives();
			will(returnValue(drives));
			oneOf(callback).showChoice(with(any(String.class)),
					with(any(String[].class)));
			will(returnValue(-1)); // The user cancelled the choice
		}});

		RemovableDrivePlugin plugin = new RemovableDrivePlugin(recogniser,
				finder, monitor);
		plugin.start(null, null, null, callback);

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
		final ConnectionRecogniser recogniser =
			context.mock(ConnectionRecogniser.class);
		final RemovableDriveFinder finder =
			context.mock(RemovableDriveFinder.class);
		final RemovableDriveMonitor monitor =
			context.mock(RemovableDriveMonitor.class);
		final BatchTransportCallback callback =
			context.mock(BatchTransportCallback.class);

		context.checking(new Expectations() {{
			oneOf(monitor).start(with(any(Callback.class)));
			oneOf(finder).findRemovableDrives();
			will(returnValue(drives));
			oneOf(callback).showChoice(with(any(String.class)),
					with(any(String[].class)));
			will(returnValue(0)); // The user chose drive1 but it doesn't exist
		}});

		RemovableDrivePlugin plugin = new RemovableDrivePlugin(recogniser,
				finder, monitor);
		plugin.start(null, null, null, callback);

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
		final ConnectionRecogniser recogniser =
			context.mock(ConnectionRecogniser.class);
		final RemovableDriveFinder finder =
			context.mock(RemovableDriveFinder.class);
		final RemovableDriveMonitor monitor =
			context.mock(RemovableDriveMonitor.class);
		final BatchTransportCallback callback =
			context.mock(BatchTransportCallback.class);

		context.checking(new Expectations() {{
			oneOf(monitor).start(with(any(Callback.class)));
			oneOf(finder).findRemovableDrives();
			will(returnValue(drives));
			oneOf(callback).showChoice(with(any(String.class)),
					with(any(String[].class)));
			will(returnValue(0)); // The user chose drive1 but it's not a dir
		}});

		RemovableDrivePlugin plugin = new RemovableDrivePlugin(recogniser,
				finder, monitor);
		plugin.start(null, null, null, callback);

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
		final ConnectionRecogniser recogniser =
			context.mock(ConnectionRecogniser.class);
		final RemovableDriveFinder finder =
			context.mock(RemovableDriveFinder.class);
		final RemovableDriveMonitor monitor =
			context.mock(RemovableDriveMonitor.class);
		final BatchTransportCallback callback =
			context.mock(BatchTransportCallback.class);

		context.checking(new Expectations() {{
			oneOf(monitor).start(with(any(Callback.class)));
			oneOf(finder).findRemovableDrives();
			will(returnValue(drives));
			oneOf(callback).showChoice(with(any(String.class)),
					with(any(String[].class)));
			will(returnValue(0)); // The user chose drive1
		}});

		RemovableDrivePlugin plugin = new RemovableDrivePlugin(recogniser,
				finder, monitor);
		plugin.start(null, null, null, callback);

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
		final ConnectionRecogniser recogniser =
			context.mock(ConnectionRecogniser.class);
		final RemovableDriveFinder finder =
			context.mock(RemovableDriveFinder.class);
		final RemovableDriveMonitor monitor =
			context.mock(RemovableDriveMonitor.class);
		final BatchTransportCallback callback =
			context.mock(BatchTransportCallback.class);

		context.checking(new Expectations() {{
			oneOf(monitor).start(with(any(Callback.class)));
			oneOf(finder).findRemovableDrives();
			will(returnValue(drives));
			oneOf(callback).showChoice(with(any(String.class)),
					with(any(String[].class)));
			will(returnValue(0)); // The user chose drive1
			oneOf(callback).showMessage(with(any(String.class)));
		}});

		RemovableDrivePlugin plugin = new RemovableDrivePlugin(recogniser,
				finder, monitor);
		plugin.start(null, null, null, callback);

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
		writer.finish();
		assertEquals(123L, files[0].length());
		// Disposing of the writer should delete the file
		writer.dispose();
		files = drive1.listFiles();
		assertTrue(files == null || files.length == 0);

		context.assertIsSatisfied();
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}
}
