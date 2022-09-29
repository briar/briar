package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.contact.event.ContactRemovedEvent;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager;
import org.briarproject.bramble.api.mailbox.MailboxStatus;
import org.briarproject.bramble.api.mailbox.MailboxUpdate;
import org.briarproject.bramble.api.mailbox.MailboxUpdateManager;
import org.briarproject.bramble.api.mailbox.MailboxUpdateWithMailbox;
import org.briarproject.bramble.api.mailbox.MailboxVersion;
import org.briarproject.bramble.api.mailbox.event.MailboxPairedEvent;
import org.briarproject.bramble.api.mailbox.event.MailboxUnpairedEvent;
import org.briarproject.bramble.api.mailbox.event.OwnMailboxConnectionStatusEvent;
import org.briarproject.bramble.api.mailbox.event.RemoteMailboxUpdateEvent;
import org.briarproject.bramble.api.plugin.Plugin.State;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.event.TransportActiveEvent;
import org.briarproject.bramble.api.plugin.event.TransportInactiveEvent;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.DbExpectations;
import org.briarproject.bramble.test.RunAction;
import org.jmock.Expectations;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.briarproject.bramble.api.mailbox.MailboxConstants.CLIENT_SUPPORTS;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.api.plugin.Plugin.State.ENABLING;
import static org.briarproject.bramble.api.plugin.TorConstants.ID;
import static org.briarproject.bramble.test.TestUtils.getContact;
import static org.briarproject.bramble.test.TestUtils.getMailboxProperties;
import static org.briarproject.nullsafety.NullSafety.requireNonNull;

public class MailboxClientManagerTest extends BrambleMockTestCase {

	private final Executor eventExecutor =
			context.mock(Executor.class, "eventExecutor");
	private final Executor dbExecutor =
			context.mock(Executor.class, "dbExecutor");
	private final TransactionManager db =
			context.mock(TransactionManager.class);
	private final ContactManager contactManager =
			context.mock(ContactManager.class);
	private final PluginManager pluginManager =
			context.mock(PluginManager.class);
	private final MailboxSettingsManager mailboxSettingsManager =
			context.mock(MailboxSettingsManager.class);
	private final MailboxUpdateManager mailboxUpdateManager =
			context.mock(MailboxUpdateManager.class);
	private final MailboxClientFactory mailboxClientFactory =
			context.mock(MailboxClientFactory.class);
	private final TorReachabilityMonitor reachabilityMonitor =
			context.mock(TorReachabilityMonitor.class);
	private final DuplexPlugin plugin = context.mock(DuplexPlugin.class);
	private final MailboxClient ownClient =
			context.mock(MailboxClient.class, "ownClient");
	private final MailboxClient contactClient =
			context.mock(MailboxClient.class, "contactClient");

	private final MailboxClientManager manager =
			new MailboxClientManager(eventExecutor, dbExecutor, db,
					contactManager, pluginManager, mailboxSettingsManager,
					mailboxUpdateManager, mailboxClientFactory,
					reachabilityMonitor);

	private final Contact contact = getContact();
	private final List<MailboxVersion> incompatibleVersions =
			singletonList(new MailboxVersion(999, 0));
	private final MailboxProperties ownProperties =
			getMailboxProperties(true, CLIENT_SUPPORTS);
	private final MailboxProperties localProperties =
			getMailboxProperties(false, CLIENT_SUPPORTS);
	private final MailboxProperties remoteProperties =
			getMailboxProperties(false, CLIENT_SUPPORTS);
	private final MailboxProperties remotePropertiesForNewMailbox =
			getMailboxProperties(false, CLIENT_SUPPORTS);
	private final MailboxUpdate localUpdateWithoutMailbox =
			new MailboxUpdate(CLIENT_SUPPORTS);
	private final MailboxUpdate remoteUpdateWithoutMailbox =
			new MailboxUpdate(CLIENT_SUPPORTS);
	private final MailboxUpdate remoteUpdateWithIncompatibleClientVersions =
			new MailboxUpdate(incompatibleVersions);
	private final MailboxUpdateWithMailbox localUpdateWithMailbox =
			new MailboxUpdateWithMailbox(CLIENT_SUPPORTS, localProperties);
	private final MailboxUpdateWithMailbox remoteUpdateWithMailbox =
			new MailboxUpdateWithMailbox(CLIENT_SUPPORTS, remoteProperties);
	private final MailboxUpdateWithMailbox remoteUpdateWithNewMailbox =
			new MailboxUpdateWithMailbox(CLIENT_SUPPORTS,
					remotePropertiesForNewMailbox);

	@Test
	public void testLoadsMailboxUpdatesAtStartupWhenOffline() throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. We're offline so there's nothing
		// else to do.
		expectLoadUpdates(localUpdateWithoutMailbox,
				remoteUpdateWithoutMailbox, null);
		expectCheckPluginState(ENABLING);
		manager.startService();

		// At shutdown there should be no clients to destroy. The manager
		// should destroy the reachability monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testLoadsMailboxUpdatesAtStartupWhenOnline() throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. We're online but we don't have a
		// mailbox and neither does the contact, so there's nothing else to do.
		expectLoadUpdates(localUpdateWithoutMailbox,
				remoteUpdateWithoutMailbox, null);
		expectCheckPluginState(ACTIVE);
		manager.startService();

		// At shutdown there should be no clients to destroy. The manager
		// should destroy the reachability monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testAssignsContactToOurMailboxIfContactHasNoMailbox()
			throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. We have a mailbox but the contact
		// doesn't.
		//
		// We're online, so the manager should create a client for our own
		// mailbox and assign the contact to it for upload and download.
		expectLoadUpdates(localUpdateWithMailbox,
				remoteUpdateWithoutMailbox, ownProperties);
		expectCheckPluginState(ACTIVE);
		expectCreateClientForOwnMailbox();
		expectAssignContactToOwnMailboxForDownload();
		expectAssignContactToOwnMailboxForUpload();
		manager.startService();

		// At shutdown the manager should destroy the client for our own
		// mailbox and the reachability monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyClientForOwnMailbox();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testDoesNotAssignContactToOurMailboxIfContactHasNotSentUpdate()
			throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. We have a mailbox but the contact
		// has never sent us an update, so the remote update is null.
		//
		// We're online, so the manager should create a client for our own
		// mailbox. We don't know what API versions the contact supports,
		// if any, so the contact should not be assigned to our mailbox.
		expectLoadUpdates(localUpdateWithMailbox, null, ownProperties);
		expectCheckPluginState(ACTIVE);
		expectCreateClientForOwnMailbox();
		manager.startService();

		// At shutdown the manager should destroy the client for our own
		// mailbox and the reachability monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyClientForOwnMailbox();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testDoesNotAssignContactToOurMailboxIfContactHasIncompatibleClientVersions()
			throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. We have a mailbox but the contact
		// doesn't. The contact's client API versions are incompatible with
		// our mailbox.
		//
		// We're online, so the manager should create a client for our own
		// mailbox. The contact's client API versions are incompatible with
		// our mailbox, so the contact should not be assigned to our mailbox.
		expectLoadUpdates(localUpdateWithMailbox,
				remoteUpdateWithIncompatibleClientVersions, ownProperties);
		expectCheckPluginState(ACTIVE);
		expectCreateClientForOwnMailbox();
		manager.startService();

		// At shutdown the manager should destroy the client for our own
		// mailbox and the reachability monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyClientForOwnMailbox();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testAssignsContactToContactMailboxIfWeHaveNoMailbox()
			throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. We don't have a mailbox but the
		// contact does.
		//
		// We're online, so the manager should create a client for the
		// contact's mailbox and assign the contact to it for upload and
		// download.
		expectLoadUpdates(localUpdateWithoutMailbox,
				remoteUpdateWithMailbox, null);
		expectCheckPluginState(ACTIVE);
		expectCreateClientForContactMailbox();
		expectAssignContactToContactMailboxForDownload(remoteProperties);
		expectAssignContactToContactMailboxForUpload(remoteProperties);
		manager.startService();

		// At shutdown the manager should destroy the client for the contact's
		// mailbox and the reachability monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyClientForContactMailbox();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testAssignsContactToBothMailboxesIfWeBothHaveMailboxes()
			throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. We have a mailbox and so does the
		// contact.
		//
		// We're online, so the manager should create clients for the
		// contact's mailbox and our mailbox. The manager should assign the
		// contact to the contact's mailbox for upload and our mailbox for
		// download.
		expectLoadUpdates(localUpdateWithMailbox,
				remoteUpdateWithMailbox, ownProperties);
		expectCheckPluginState(ACTIVE);
		expectCreateClientForContactMailbox();
		expectAssignContactToContactMailboxForUpload(remoteProperties);
		expectCreateClientForOwnMailbox();
		expectAssignContactToOwnMailboxForDownload();
		manager.startService();

		// At shutdown the manager should destroy the client for the contact's
		// mailbox, the client for our own mailbox, and the reachability
		// monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyClientForContactMailbox();
		expectDestroyClientForOwnMailbox();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testCreatesClientsWhenTorBecomesActive() throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. We have a mailbox but the contact
		// doesn't. We're offline so there's nothing else to do.
		expectLoadUpdates(localUpdateWithMailbox,
				remoteUpdateWithoutMailbox, ownProperties);
		expectCheckPluginState(ENABLING);
		manager.startService();

		// When we come online, the manager should create a client for our own
		// mailbox and assign the contact to it for upload and download.
		expectCreateClientForOwnMailbox();
		expectAssignContactToOwnMailboxForDownload();
		expectAssignContactToOwnMailboxForUpload();
		manager.eventOccurred(new TransportActiveEvent(ID));

		// When we go offline, the manager should destroy the client for our
		// own mailbox.
		expectDestroyClientForOwnMailbox();
		manager.eventOccurred(new TransportInactiveEvent(ID));

		// At shutdown the manager should destroy the reachability monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testAssignsContactToOurMailboxWhenPaired() throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. We're online but we don't have a
		// mailbox and neither does the contact, so there's nothing else to do.
		expectLoadUpdates(localUpdateWithoutMailbox,
				remoteUpdateWithoutMailbox, null);
		expectCheckPluginState(ACTIVE);
		manager.startService();

		// When we pair a mailbox, the manager should create a client for our
		// mailbox and assign the contact to our mailbox for upload and
		// download.
		expectCreateClientForOwnMailbox();
		expectAssignContactToOwnMailboxForUpload();
		expectAssignContactToOwnMailboxForDownload();
		manager.eventOccurred(new MailboxPairedEvent(ownProperties,
				singletonMap(contact.getId(), localUpdateWithMailbox)));

		// At shutdown the manager should destroy the client for our mailbox
		// and the reachability monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyClientForOwnMailbox();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testReassignsContactToOurMailboxWhenPaired() throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. The contact has a mailbox but we
		// don't.
		//
		// We're online, so the manager should create a client for the
		// contact's mailbox and assign the contact to it for upload and
		// download.
		expectLoadUpdates(localUpdateWithoutMailbox,
				remoteUpdateWithMailbox, null);
		expectCheckPluginState(ACTIVE);
		expectCreateClientForContactMailbox();
		expectAssignContactToContactMailboxForDownload(remoteProperties);
		expectAssignContactToContactMailboxForUpload(remoteProperties);
		manager.startService();

		// When we pair a mailbox, the manager should create a client for our
		// mailbox and reassign the contact to our mailbox for download.
		expectCreateClientForOwnMailbox();
		expectDeassignContactFromContactMailboxForDownload();
		expectAssignContactToOwnMailboxForDownload();
		manager.eventOccurred(new MailboxPairedEvent(ownProperties,
				singletonMap(contact.getId(), localUpdateWithMailbox)));

		// At shutdown the manager should destroy the client for the contact's
		// mailbox, the client for our own mailbox, and the reachability
		// monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyClientForContactMailbox();
		expectDestroyClientForOwnMailbox();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testDoesNotAssignContactWhenPairedIfContactHasNotSentUpdate()
			throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. We don't have a mailbox and the
		// contact has never sent us an update, so the remote update is null.
		expectLoadUpdates(localUpdateWithoutMailbox, null, null);
		expectCheckPluginState(ACTIVE);
		manager.startService();

		// When we pair a mailbox, the manager should create a client for our
		// mailbox. We don't know whether the contact can use our mailbox, so
		// the contact should not be assigned to our mailbox.
		expectCreateClientForOwnMailbox();
		manager.eventOccurred(new MailboxPairedEvent(ownProperties,
				singletonMap(contact.getId(), localUpdateWithMailbox)));

		// At shutdown the manager should destroy the client for our mailbox
		// and the reachability monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyClientForOwnMailbox();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testDoesNotAssignContactWhenPairedIfContactHasIncompatibleClientVersions()
			throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. We don't have a mailbox and neither
		// does the contact. The contact's client API versions are
		// incompatible with our mailbox.
		expectLoadUpdates(localUpdateWithoutMailbox,
				remoteUpdateWithIncompatibleClientVersions, null);
		expectCheckPluginState(ACTIVE);
		manager.startService();

		// When we pair a mailbox, the manager should create a client for our
		// mailbox. The contact's client API versions are incompatible with
		// our mailbox, so the contact should not be assigned to our mailbox.
		expectCreateClientForOwnMailbox();
		manager.eventOccurred(new MailboxPairedEvent(ownProperties,
				singletonMap(contact.getId(), localUpdateWithMailbox)));

		// At shutdown the manager should destroy the client for our mailbox
		// and the reachability monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyClientForOwnMailbox();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testReassignsContactToContactMailboxWhenUnpaired()
			throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. We have a mailbox and so does the
		// contact.
		//
		// We're online, so the manager should create clients for the
		// contact's mailbox and our mailbox. The manager should assign the
		// contact to the contact's mailbox for upload and our mailbox for
		// download.
		expectLoadUpdates(localUpdateWithMailbox,
				remoteUpdateWithMailbox, ownProperties);
		expectCheckPluginState(ACTIVE);
		expectCreateClientForContactMailbox();
		expectAssignContactToContactMailboxForUpload(remoteProperties);
		expectCreateClientForOwnMailbox();
		expectAssignContactToOwnMailboxForDownload();
		manager.startService();

		// When we unpair our mailbox, the manager should destroy the client
		// for our mailbox and reassign the contact to the contact's mailbox
		// for download.
		expectDestroyClientForOwnMailbox();
		expectAssignContactToContactMailboxForDownload(remoteProperties);
		manager.eventOccurred(new MailboxUnpairedEvent(
				singletonMap(contact.getId(), localUpdateWithoutMailbox)));

		// At shutdown the manager should destroy the client for the contact's
		// mailbox and the reachability monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyClientForContactMailbox();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testDeassignsContactForUploadAndDownloadWhenContactRemoved()
			throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. We have a mailbox but the contact
		// doesn't.
		//
		// We're online, so the manager should create a client for our mailbox.
		// The manager should assign the contact to our mailbox for upload and
		// download.
		expectLoadUpdates(localUpdateWithMailbox,
				remoteUpdateWithoutMailbox, ownProperties);
		expectCheckPluginState(ACTIVE);
		expectCreateClientForOwnMailbox();
		expectAssignContactToOwnMailboxForUpload();
		expectAssignContactToOwnMailboxForDownload();
		manager.startService();

		// When the contact is removed, the manager should deassign the contact
		// from our mailbox for upload and download.
		expectDeassignContactFromOwnMailboxForUpload();
		expectDeassignContactFromOwnMailboxForDownload();
		manager.eventOccurred(new ContactRemovedEvent(contact.getId()));

		// At shutdown the manager should destroy the client for our mailbox
		// and the reachability monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyClientForOwnMailbox();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testDeassignsContactForDownloadWhenContactRemoved()
			throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. We have a mailbox and so does the
		// contact.
		//
		// We're online, so the manager should create clients for the
		// contact's mailbox and our mailbox. The manager should assign the
		// contact to the contact's mailbox for upload and our mailbox for
		// download.
		expectLoadUpdates(localUpdateWithMailbox,
				remoteUpdateWithMailbox, ownProperties);
		expectCheckPluginState(ACTIVE);
		expectCreateClientForContactMailbox();
		expectAssignContactToContactMailboxForUpload(remoteProperties);
		expectCreateClientForOwnMailbox();
		expectAssignContactToOwnMailboxForDownload();
		manager.startService();

		// When the contact is removed, the manager should destroy the client
		// for the contact's mailbox and deassign the contact from our mailbox
		// for download.
		expectDestroyClientForContactMailbox();
		expectDeassignContactFromOwnMailboxForDownload();
		manager.eventOccurred(new ContactRemovedEvent(contact.getId()));

		// At shutdown the manager should destroy the client for our mailbox
		// and the reachability monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyClientForOwnMailbox();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testAssignsContactToContactMailboxWhenContactPairsMailbox()
			throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. We're online but we don't have a
		// mailbox and neither does the contact, so there's nothing else to do.
		expectLoadUpdates(localUpdateWithoutMailbox,
				remoteUpdateWithoutMailbox, null);
		expectCheckPluginState(ACTIVE);
		manager.startService();

		// When the contact pairs a mailbox, the manager should create a client
		// for the contact's mailbox and assign the contact to the contact's
		// mailbox for upload and download.
		expectCreateClientForContactMailbox();
		expectAssignContactToContactMailboxForUpload(remoteProperties);
		expectAssignContactToContactMailboxForDownload(remoteProperties);
		manager.eventOccurred(new RemoteMailboxUpdateEvent(contact.getId(),
				remoteUpdateWithMailbox));

		// At shutdown the manager should destroy the client for the contact's
		// mailbox and the reachability monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyClientForContactMailbox();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testReassignsContactForUploadWhenContactPairsMailbox()
			throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. We have a mailbox but the contact
		// doesn't.
		//
		// We're online, so the manager should create a client for our mailbox.
		// The manager should assign the contact to our mailbox for upload and
		// download.
		expectLoadUpdates(localUpdateWithMailbox,
				remoteUpdateWithoutMailbox, ownProperties);
		expectCheckPluginState(ACTIVE);
		expectCreateClientForOwnMailbox();
		expectAssignContactToOwnMailboxForUpload();
		expectAssignContactToOwnMailboxForDownload();
		manager.startService();

		// When the contact pairs a mailbox, the manager should create a client
		// for the contact's mailbox and reassign the contact to the contact's
		// mailbox for upload.
		expectCreateClientForContactMailbox();
		expectDeassignContactFromOwnMailboxForUpload();
		expectAssignContactToContactMailboxForUpload(remoteProperties);
		manager.eventOccurred(new RemoteMailboxUpdateEvent(contact.getId(),
				remoteUpdateWithMailbox));

		// At shutdown the manager should destroy the client for the contact's
		// mailbox, the client for our own mailbox, and the reachability
		// monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyClientForContactMailbox();
		expectDestroyClientForOwnMailbox();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testReassignsContactForUploadWhenContactUnpairsMailbox()
			throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. We have a mailbox and so does the
		// contact.
		//
		// We're online, so the manager should create clients for the
		// contact's mailbox and our mailbox. The manager should assign the
		// contact to the contact's mailbox for upload and our mailbox for
		// download.
		expectLoadUpdates(localUpdateWithMailbox,
				remoteUpdateWithMailbox, ownProperties);
		expectCheckPluginState(ACTIVE);
		expectCreateClientForContactMailbox();
		expectAssignContactToContactMailboxForUpload(remoteProperties);
		expectCreateClientForOwnMailbox();
		expectAssignContactToOwnMailboxForDownload();
		manager.startService();

		// When the contact unpairs their mailbox, the manager should destroy
		// the client for the contact's mailbox and reassign the contact to
		// our mailbox for upload.
		expectDestroyClientForContactMailbox();
		expectAssignContactToOwnMailboxForUpload();
		manager.eventOccurred(new RemoteMailboxUpdateEvent(contact.getId(),
				remoteUpdateWithoutMailbox));

		// At shutdown the manager should destroy the client for our mailbox
		// and the reachability monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyClientForOwnMailbox();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testReassignsContactForUploadWhenContactReplacesMailbox()
			throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. We have a mailbox and so does the
		// contact.
		//
		// We're online, so the manager should create clients for the
		// contact's mailbox and our mailbox. The manager should assign the
		// contact to the contact's mailbox for upload and our mailbox for
		// download.
		expectLoadUpdates(localUpdateWithMailbox,
				remoteUpdateWithMailbox, ownProperties);
		expectCheckPluginState(ACTIVE);
		expectCreateClientForContactMailbox();
		expectAssignContactToContactMailboxForUpload(remoteProperties);
		expectCreateClientForOwnMailbox();
		expectAssignContactToOwnMailboxForDownload();
		manager.startService();

		// When the contact replaces their mailbox, the manager should replace
		// the client for the contact's mailbox and assign the contact to
		// the contact's new mailbox for upload.
		expectDestroyClientForContactMailbox();
		expectCreateClientForContactMailbox();
		expectAssignContactToContactMailboxForUpload(
				remotePropertiesForNewMailbox);
		manager.eventOccurred(new RemoteMailboxUpdateEvent(contact.getId(),
				remoteUpdateWithNewMailbox));

		// At shutdown the manager should destroy the client for the contact's
		// mailbox, the client for our own mailbox, and the reachability
		// monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyClientForContactMailbox();
		expectDestroyClientForOwnMailbox();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testReassignsContactWhenContactReplacesMailbox()
			throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. We don't have a mailbox but the
		// contact does.
		//
		// We're online, so the manager should create a client for the
		// contact's mailbox and assign the contact to the contact's mailbox
		// for upload and download.
		expectLoadUpdates(localUpdateWithoutMailbox,
				remoteUpdateWithMailbox, null);
		expectCheckPluginState(ACTIVE);
		expectCreateClientForContactMailbox();
		expectAssignContactToContactMailboxForUpload(remoteProperties);
		expectAssignContactToContactMailboxForDownload(remoteProperties);
		manager.startService();

		// When the contact replaces their mailbox, the manager should replace
		// the client for the contact's mailbox and assign the contact to
		// the contact's new mailbox for upload and download.
		expectDestroyClientForContactMailbox();
		expectCreateClientForContactMailbox();
		expectAssignContactToContactMailboxForUpload(
				remotePropertiesForNewMailbox);
		expectAssignContactToContactMailboxForDownload(
				remotePropertiesForNewMailbox);
		manager.eventOccurred(new RemoteMailboxUpdateEvent(contact.getId(),
				remoteUpdateWithNewMailbox));

		// At shutdown the manager should destroy the client for the contact's
		// mailbox and the reachability monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyClientForContactMailbox();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testDoesNotReassignContactWhenRemotePropertiesAreUnchanged()
			throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. We don't have a mailbox but the
		// contact does.
		//
		// We're online, so the manager should create a client for the
		// contact's mailbox and assign the contact to the contact's mailbox
		// for upload and download.
		expectLoadUpdates(localUpdateWithoutMailbox,
				remoteUpdateWithMailbox, null);
		expectCheckPluginState(ACTIVE);
		expectCreateClientForContactMailbox();
		expectAssignContactToContactMailboxForUpload(remoteProperties);
		expectAssignContactToContactMailboxForDownload(remoteProperties);
		manager.startService();

		// When the contact sends an update with unchanged properties, the
		// clients and assignments should not be affected.
		manager.eventOccurred(new RemoteMailboxUpdateEvent(contact.getId(),
				remoteUpdateWithMailbox));

		// At shutdown the manager should destroy the client for the contact's
		// mailbox and the reachability monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyClientForContactMailbox();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testAssignsContactToOurMailboxWhenClientVersionsBecomeCompatible()
			throws Exception {
		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. We have a mailbox but the contact
		// doesn't. The contact's client API versions are incompatible with
		// our mailbox.
		//
		// We're online, so the manager should create a client for our own
		// mailbox. The contact's client API versions are incompatible with
		// our mailbox, so the contact should not be assigned to our mailbox.
		expectLoadUpdates(localUpdateWithMailbox,
				remoteUpdateWithIncompatibleClientVersions, ownProperties);
		expectCheckPluginState(ACTIVE);
		expectCreateClientForOwnMailbox();
		manager.startService();

		// When the contact sends an update indicating that their client API
		// versions are now compatible with our mailbox, the manager should
		// assign the contact to our mailbox for upload and download.
		expectAssignContactToOwnMailboxForUpload();
		expectAssignContactToOwnMailboxForDownload();
		manager.eventOccurred(new RemoteMailboxUpdateEvent(contact.getId(),
				remoteUpdateWithoutMailbox));

		// At shutdown the manager should destroy the client for our own
		// mailbox and the reachability monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyClientForOwnMailbox();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	@Test
	public void testRecreatesClientsWhenOwnMailboxServerVersionsChange()
			throws Exception {
		long now = System.currentTimeMillis();
		List<MailboxVersion> compatibleVersions =
				new ArrayList<>(CLIENT_SUPPORTS);
		compatibleVersions.add(new MailboxVersion(999, 0));
		MailboxStatus mailboxStatus =
				new MailboxStatus(now, now, 0, compatibleVersions);

		// At startup the manager should load the local and remote updates
		// and our own mailbox properties. The contact has a mailbox, so the
		// remote update contains the properties received from the contact.
		// We also have a mailbox, so the local update contains the properties
		// we sent to the contact.
		//
		// We're online, so the manager should create clients for the
		// contact's mailbox and our mailbox. The manager should assign the
		// contact to the contact's mailbox for upload and our mailbox for
		// download.
		expectLoadUpdates(localUpdateWithMailbox,
				remoteUpdateWithMailbox, ownProperties);
		expectCheckPluginState(ACTIVE);
		expectCreateClientForContactMailbox();
		expectAssignContactToContactMailboxForUpload(remoteProperties);
		expectCreateClientForOwnMailbox();
		expectAssignContactToOwnMailboxForDownload();
		manager.startService();

		// When we learn that our mailbox's API versions have changed, the
		// manager should destroy and recreate the clients for our own mailbox
		// and the contact's mailbox and assign the contact to the new clients.
		expectDestroyClientForContactMailbox();
		expectDestroyClientForOwnMailbox();
		expectCreateClientForContactMailbox();
		expectAssignContactToContactMailboxForUpload(remoteProperties);
		expectCreateClientForOwnMailbox();
		expectAssignContactToOwnMailboxForDownload();
		manager.eventOccurred(
				new OwnMailboxConnectionStatusEvent(mailboxStatus));

		// At shutdown the manager should destroy the client for the contact's
		// mailbox, the client for our own mailbox, and the reachability
		// monitor.
		expectRunTaskOnEventExecutor();
		expectDestroyClientForContactMailbox();
		expectDestroyClientForOwnMailbox();
		expectDestroyReachabilityMonitor();
		manager.stopService();
	}

	private void expectLoadUpdates(MailboxUpdate local,
			@Nullable MailboxUpdate remote,
			@Nullable MailboxProperties own) throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			oneOf(reachabilityMonitor).start();
			oneOf(dbExecutor).execute(with(any(Runnable.class)));
			will(new RunAction());
			oneOf(db).transaction(with(true), withDbRunnable(txn));
			oneOf(contactManager).getContacts(txn);
			will(returnValue(singletonList(contact)));
			oneOf(mailboxUpdateManager).getLocalUpdate(txn, contact.getId());
			will(returnValue(local));
			oneOf(mailboxUpdateManager).getRemoteUpdate(txn, contact.getId());
			will(returnValue(remote));
			oneOf(mailboxSettingsManager).getOwnMailboxProperties(txn);
			will(returnValue(own));
		}});
	}

	private void expectCheckPluginState(State state) {
		context.checking(new Expectations() {{
			oneOf(pluginManager).getPlugin(ID);
			will(returnValue(plugin));
			oneOf(plugin).getState();
			will(returnValue(state));
		}});
	}

	private void expectCreateClientForOwnMailbox() {
		context.checking(new Expectations() {{
			oneOf(mailboxClientFactory).createOwnMailboxClient(
					reachabilityMonitor, ownProperties);
			will(returnValue(ownClient));
			oneOf(ownClient).start();
		}});
	}

	private void expectCreateClientForContactMailbox() {
		context.checking(new Expectations() {{
			oneOf(mailboxClientFactory)
					.createContactMailboxClient(reachabilityMonitor);
			will(returnValue(contactClient));
			oneOf(contactClient).start();
		}});
	}

	private void expectAssignContactToOwnMailboxForDownload() {
		context.checking(new Expectations() {{
			oneOf(ownClient).assignContactForDownload(contact.getId(),
					ownProperties,
					requireNonNull(localProperties.getOutboxId()));
		}});
	}

	private void expectAssignContactToOwnMailboxForUpload() {
		context.checking(new Expectations() {{
			oneOf(ownClient).assignContactForUpload(contact.getId(),
					ownProperties,
					requireNonNull(localProperties.getInboxId()));
		}});
	}

	private void expectAssignContactToContactMailboxForDownload(
			MailboxProperties remoteProperties) {
		context.checking(new Expectations() {{
			oneOf(contactClient).assignContactForDownload(contact.getId(),
					remoteProperties,
					requireNonNull(remoteProperties.getInboxId()));
		}});
	}

	private void expectAssignContactToContactMailboxForUpload(
			MailboxProperties remoteProperties) {
		context.checking(new Expectations() {{
			oneOf(contactClient).assignContactForUpload(contact.getId(),
					remoteProperties,
					requireNonNull(remoteProperties.getOutboxId()));
		}});
	}

	private void expectDeassignContactFromOwnMailboxForUpload() {
		context.checking(new Expectations() {{
			oneOf(ownClient).deassignContactForUpload(contact.getId());
		}});
	}

	private void expectDeassignContactFromOwnMailboxForDownload() {
		context.checking(new Expectations() {{
			oneOf(ownClient).deassignContactForDownload(contact.getId());
		}});
	}

	private void expectDeassignContactFromContactMailboxForDownload() {
		context.checking(new Expectations() {{
			oneOf(contactClient).deassignContactForDownload(contact.getId());
		}});
	}

	private void expectRunTaskOnEventExecutor() {
		context.checking(new Expectations() {{
			oneOf(eventExecutor).execute(with(any(Runnable.class)));
			will(new RunAction());
		}});
	}

	private void expectDestroyClientForOwnMailbox() {
		context.checking(new Expectations() {{
			oneOf(ownClient).destroy();
		}});
	}

	private void expectDestroyClientForContactMailbox() {
		context.checking(new Expectations() {{
			oneOf(contactClient).destroy();
		}});
	}

	private void expectDestroyReachabilityMonitor() {
		context.checking(new Expectations() {{
			oneOf(reachabilityMonitor).destroy();
		}});
	}
}
