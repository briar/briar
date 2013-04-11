package net.sf.briar.android.invitation;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.android.AuthorNameComparator;
import net.sf.briar.android.BriarActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.android.LocalAuthorSpinnerAdapter;
import net.sf.briar.api.AuthorId;
import net.sf.briar.api.LocalAuthor;
import net.sf.briar.api.android.BundleEncrypter;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.android.ReferenceManager;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.invitation.InvitationListener;
import net.sf.briar.api.invitation.InvitationState;
import net.sf.briar.api.invitation.InvitationTask;
import net.sf.briar.api.invitation.InvitationTaskFactory;
import android.content.Intent;
import android.os.Bundle;

import com.google.inject.Inject;

public class AddContactActivity extends BriarActivity
implements InvitationListener {

	private static final Logger LOG =
			Logger.getLogger(AddContactActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private BundleEncrypter bundleEncrypter;
	@Inject private CryptoComponent crypto;
	@Inject private InvitationTaskFactory invitationTaskFactory;
	@Inject private ReferenceManager referenceManager;
	private AddContactView view = null;
	private InvitationTask task = null;
	private long taskHandle = -1;
	private AuthorId localAuthorId = null;
	private String networkName = null;
	private boolean useBluetooth = false;
	private int localInvitationCode = -1, remoteInvitationCode = -1;
	private int localConfirmationCode = -1, remoteConfirmationCode = -1;
	private boolean connectionFailed = false;
	private boolean localCompared = false, remoteCompared = false;
	private boolean localMatched = false, remoteMatched = false;
	private String contactName = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);
		if(state == null || !bundleEncrypter.decrypt(state)) {
			// This is a new activity or the app has restarted
			setView(new NetworkSetupView(this));
		} else {
			// Restore the activity's state
			byte[] b = state.getByteArray("net.sf.briar.LOCAL_AUTHOR_ID");
			if(b != null) localAuthorId = new AuthorId(b);
			networkName = state.getString("net.sf.briar.NETWORK_NAME");
			useBluetooth = state.getBoolean("net.sf.briar.USE_BLUETOOTH");
			taskHandle = state.getLong("net.sf.briar.TASK_HANDLE", -1);
			task = referenceManager.getReference(taskHandle,
					InvitationTask.class);
			if(task == null) {
				// No background task - we must be in an initial or final state
				localInvitationCode = state.getInt("net.sf.briar.LOCAL_CODE");
				remoteInvitationCode = state.getInt("net.sf.briar.REMOTE_CODE");
				connectionFailed = state.getBoolean("net.sf.briar.FAILED");
				contactName = state.getString("net.sf.briar.CONTACT_NAME");
				if(contactName != null) {
					localCompared = remoteCompared = true;
					localMatched = remoteMatched = true;
				}
				// Set the appropriate view for the state
				if(localInvitationCode == -1) {
					setView(new NetworkSetupView(this));
				} else if(remoteInvitationCode == -1) {
					setView(new InvitationCodeView(this));
				} else if(connectionFailed) {
					setView(new ConnectionFailedView(this));
				} else if(contactName == null) {
					setView(new CodesDoNotMatchView(this));
				} else {
					setView(new ContactAddedView(this));
				}
			} else {
				// A background task exists - listen to it and get its state
				InvitationState s = task.addListener(this);
				localInvitationCode = s.getLocalInvitationCode();
				remoteInvitationCode = s.getRemoteInvitationCode();
				localConfirmationCode = s.getLocalConfirmationCode();
				remoteConfirmationCode = s.getRemoteConfirmationCode();
				connectionFailed = s.getConnectionFailed();
				localCompared = s.getLocalCompared();
				remoteCompared = s.getRemoteCompared();
				localMatched = s.getLocalMatched();
				remoteMatched = s.getRemoteMatched();
				contactName = s.getContactName();
				// Set the appropriate view for the state
				if(localInvitationCode == -1) {
					setView(new NetworkSetupView(this));
				} else if(remoteInvitationCode == -1) {
					setView(new InvitationCodeView(this));
				} else if(localConfirmationCode == -1) {
					setView(new ConnectionView(this));
				} else if(connectionFailed) {
					setView(new ConnectionFailedView(this));
				} else if(!localCompared) {
					setView(new ConfirmationCodeView(this));
				} else if(!remoteCompared) {
					setView(new WaitForContactView(this));
				} else if(localMatched && remoteMatched) {
					if(contactName == null)
						setView(new WaitForContactView(this));
					else setView(new ContactAddedView(this));
				} else {
					setView(new CodesDoNotMatchView(this));
				}
			}
		}

		// Bind to the service so we can wait for it to start
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
	}

	@Override
	public void onResume() {
		super.onResume();
		view.populate();
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		if(localAuthorId != null) {
			state.putByteArray("net.sf.briar.LOCAL_AUTHOR_ID",
					localAuthorId.getBytes());
		}
		state.putString("net.sf.briar.NETWORK_NAME", networkName);
		state.putBoolean("net.sf.briar.USE_BLUETOOTH", useBluetooth);
		state.putInt("net.sf.briar.LOCAL_CODE", localInvitationCode);
		state.putInt("net.sf.briar.REMOTE_CODE", remoteInvitationCode);
		state.putBoolean("net.sf.briar.FAILED", connectionFailed);
		state.putString("net.sf.briar.CONTACT_NAME", contactName);
		if(task != null) state.putLong("net.sf.briar.TASK_HANDLE", taskHandle);
		bundleEncrypter.encrypt(state);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if(task != null) task.removeListener(this);
		unbindService(serviceConnection);
	}

	void setView(AddContactView view) {
		this.view = view;
		view.init(this);
		setContentView(view);
	}

	void reset(AddContactView view) {
		// Note: localAuthorId is not reset
		task = null;
		taskHandle = -1;
		networkName = null;
		useBluetooth = false;
		localInvitationCode = -1;
		localConfirmationCode = remoteConfirmationCode = -1;
		connectionFailed = false;
		localCompared = remoteCompared = false;
		localMatched = remoteMatched = false;
		contactName = null;
		setView(view);
	}

	void loadLocalAuthors(final LocalAuthorSpinnerAdapter adapter) {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					long now = System.currentTimeMillis();
					Collection<LocalAuthor> localAuthors = db.getLocalAuthors();
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Loading authors took " + duration + " ms");
					displayLocalAuthors(adapter, localAuthors);
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	private void displayLocalAuthors(final LocalAuthorSpinnerAdapter adapter,
			final Collection<LocalAuthor> localAuthors) {
		runOnUiThread(new Runnable() {
			public void run() {
				adapter.clear();
				for(LocalAuthor a : localAuthors) adapter.add(a);
				adapter.sort(AuthorNameComparator.INSTANCE);
			}
		});
	}

	void setLocalAuthorId(AuthorId localAuthorId) {
		this.localAuthorId = localAuthorId;
	}

	AuthorId getLocalAuthorId() {
		return localAuthorId;
	}

	void setNetworkName(String networkName) {
		this.networkName = networkName;
	}

	String getNetworkName() {
		return networkName;
	}

	void setUseBluetooth(boolean useBluetooth) {
		this.useBluetooth = useBluetooth;
	}

	boolean getUseBluetooth() {
		return useBluetooth;
	}

	int getLocalInvitationCode() {
		if(localInvitationCode == -1)
			localInvitationCode = crypto.generateInvitationCode();
		return localInvitationCode;
	}

	void remoteInvitationCodeEntered(int code) {
		setView(new ConnectionView(this));
		if(localAuthorId == null) throw new IllegalStateException();
		if(localInvitationCode == -1) throw new IllegalStateException();
		task = invitationTaskFactory.createTask(localAuthorId,
				localInvitationCode, code);
		taskHandle = referenceManager.putReference(task, InvitationTask.class);
		task.addListener(AddContactActivity.this);
		task.addListener(new ReferenceCleaner(referenceManager, taskHandle));
		task.connect();
	}

	int getLocalConfirmationCode() {
		return localConfirmationCode;
	}

	void remoteConfirmationCodeEntered(int code) {
		localCompared = true;
		if(code == remoteConfirmationCode) {
			localMatched = true;
			setView(new WaitForContactView(this));
			task.localConfirmationSucceeded();
		} else {
			setView(new CodesDoNotMatchView(this));
			task.localConfirmationFailed();
		}
	}

	String getContactName() {
		return contactName;
	}

	public void connectionSucceeded(final int localCode, final int remoteCode) {
		runOnUiThread(new Runnable() {
			public void run() {
				localConfirmationCode = localCode;
				remoteConfirmationCode = remoteCode;
				setView(new ConfirmationCodeView(AddContactActivity.this));
			}
		});
	}

	public void connectionFailed() {
		runOnUiThread(new Runnable() {
			public void run() {
				connectionFailed = true;
				setView(new ConnectionFailedView(AddContactActivity.this));
			}
		});
	}

	public void remoteConfirmationSucceeded() {
		runOnUiThread(new Runnable() {
			public void run() {
				remoteCompared = true;
				remoteMatched = true;
			}
		});
	}

	public void remoteConfirmationFailed() {
		runOnUiThread(new Runnable() {
			public void run() {
				remoteCompared = true;
				if(localMatched)
					setView(new CodesDoNotMatchView(AddContactActivity.this));
			}
		});
	}

	public void pseudonymExchangeSucceeded(final String remoteName) {
		runOnUiThread(new Runnable() {
			public void run() {
				contactName = remoteName;
				setView(new ContactAddedView(AddContactActivity.this));
			}
		});
	}

	public void pseudonymExchangeFailed() {
		runOnUiThread(new Runnable() {
			public void run() {
				setView(new ConnectionFailedView(AddContactActivity.this));
			}
		});
	}

	/**
	 * Cleans up the reference to the invitation task when the task completes.
	 * This class is static to prevent memory leaks.
	 */
	private static class ReferenceCleaner implements InvitationListener {

		private final ReferenceManager referenceManager;
		private final long handle;

		private ReferenceCleaner(ReferenceManager referenceManager,
				long handle) {
			this.referenceManager = referenceManager;
			this.handle = handle;
		}

		public void connectionSucceeded(int localCode, int remoteCode) {
			// Wait for remote confirmation to succeed or fail
		}

		public void connectionFailed() {
			// FIXME: Do this on the UI thread
			referenceManager.removeReference(handle, InvitationTask.class);
		}

		public void remoteConfirmationSucceeded() {
			// Wait for the pseudonym exchange to succeed or fail
		}

		public void remoteConfirmationFailed() {
			// FIXME: Do this on the UI thread
			referenceManager.removeReference(handle, InvitationTask.class);
		}

		public void pseudonymExchangeSucceeded(String remoteName) {
			// FIXME: Do this on the UI thread
			referenceManager.removeReference(handle, InvitationTask.class);
		}

		public void pseudonymExchangeFailed() {
			// FIXME: Do this on the UI thread
			referenceManager.removeReference(handle, InvitationTask.class);
		}
	}
}
