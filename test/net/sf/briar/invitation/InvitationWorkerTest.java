package net.sf.briar.invitation;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.invitation.InvitationCallback;
import net.sf.briar.api.invitation.InvitationParameters;
import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class InvitationWorkerTest extends TestCase {

	private final File testDir = TestUtils.getTestDirectory();

	@Before
	public void setUp() {
		testDir.mkdirs();
	}

	@Test
	public void testHaltsIfDestinationDoesNotExist() {
		final File nonExistent = new File(testDir, "does.not.exist");
		Mockery context = new Mockery();
		final InvitationCallback callback =
			context.mock(InvitationCallback.class);
		final InvitationParameters params =
			context.mock(InvitationParameters.class);
		final DatabaseComponent database =
			context.mock(DatabaseComponent.class);
		final WriterFactory writerFactory = context.mock(WriterFactory.class);
		context.checking(new Expectations() {{
			oneOf(params).getChosenLocation();
			will(returnValue(nonExistent));
			oneOf(callback).notFound(nonExistent);
		}});

		new InvitationWorker(callback, params, database, writerFactory).run();

		context.assertIsSatisfied();
		File[] children = testDir.listFiles();
		assertNotNull(children);
		assertEquals(0, children.length);
	}

	@Test
	public void testHaltsIfDestinationIsNotADirectory() throws IOException {
		final File exists = new File(testDir, "exists");
		TestUtils.createFile(exists, "foo");
		assertFalse(exists.isDirectory());
		Mockery context = new Mockery();
		final InvitationCallback callback =
			context.mock(InvitationCallback.class);
		final InvitationParameters params =
			context.mock(InvitationParameters.class);
		final DatabaseComponent database =
			context.mock(DatabaseComponent.class);
		final WriterFactory writerFactory = context.mock(WriterFactory.class);
		context.checking(new Expectations() {{
			oneOf(params).getChosenLocation();
			will(returnValue(exists));
			oneOf(callback).notDirectory(exists);
		}});

		new InvitationWorker(callback, params, database, writerFactory).run();

		context.assertIsSatisfied();
		File[] children = testDir.listFiles();
		assertNotNull(children);
		assertEquals(1, children.length);
		assertEquals(exists, children[0]);
	}

	@Test
	public void testCreatesExe() throws IOException, DbException {
		testInstallerCreation(true, false);
	}

	@Test
	public void testCreatesJar() throws IOException, DbException {
		testInstallerCreation(false, true);
	}

	@Test
	public void testCreatesBoth() throws IOException, DbException {
		testInstallerCreation(true, true);
	}

	@Test
	public void testCreatesNeither() throws IOException, DbException {
		testInstallerCreation(false, false);
	}

	private void testInstallerCreation(final boolean createExe,
			final boolean createJar) throws IOException, DbException {
		TransportId transportId = new TransportId(TestUtils.getRandomId());
		TransportIndex transportIndex = new TransportIndex(13);
		Transport transport = new Transport(transportId, transportIndex,
			Collections.singletonMap("foo", "bar"));
		final Collection<Transport> transports =
			Collections.singletonList(transport);
		final File setup = new File(testDir, "setup.dat");
		TestUtils.createFile(setup, "foo bar baz");
		final File invitation = new File(testDir, "invitation.dat");
		final File exe = new File(testDir, "briar.exe");
		final File jar = new File(testDir, "briar.jar");
		assertTrue(setup.exists());
		assertFalse(invitation.exists());
		assertFalse(exe.exists());
		assertFalse(jar.exists());
		final List<File> expectedFiles = new ArrayList<File>();
		expectedFiles.add(invitation);
		if(createExe) expectedFiles.add(exe);
		if(createJar) expectedFiles.add(jar);
		Mockery context = new Mockery();
		final InvitationCallback callback =
			context.mock(InvitationCallback.class);
		final InvitationParameters params =
			context.mock(InvitationParameters.class);
		final DatabaseComponent database =
			context.mock(DatabaseComponent.class);
		final WriterFactory writerFactory = context.mock(WriterFactory.class);
		final Writer writer = context.mock(Writer.class);
		context.checking(new Expectations() {{
			oneOf(params).getChosenLocation();
			will(returnValue(testDir));
			allowing(callback).isCancelled();
			will(returnValue(false));
			oneOf(params).getPassword();
			will(returnValue(new char[] {'x', 'y', 'z', 'z', 'y'}));
			oneOf(callback).encryptingFile(invitation);
			oneOf(database).getLocalTransports();
			will(returnValue(transports));
			oneOf(writerFactory).createWriter(with(any(OutputStream.class)));
			will(returnValue(writer));
			oneOf(writer).writeList(transports);
			oneOf(params).shouldCreateExe();
			will(returnValue(createExe));
			oneOf(params).shouldCreateJar();
			will(returnValue(createJar));
			if(createExe) {
				oneOf(params).getSetupDat();
				will(returnValue(setup));
				oneOf(callback).copyingFile(exe);
			}
			if(createJar) {
				oneOf(params).getSetupDat();
				will(returnValue(setup));
				oneOf(callback).copyingFile(jar);
			}
			oneOf(callback).created(expectedFiles);
		}});

		new InvitationWorker(callback, params, database, writerFactory).run();

		assertTrue(invitation.exists());
		assertEquals(createExe, exe.exists());
		assertEquals(createJar, jar.exists());
		if(createExe) assertEquals(exe.length(), setup.length());
		if(createJar) assertEquals(jar.length(), setup.length());
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}
}
