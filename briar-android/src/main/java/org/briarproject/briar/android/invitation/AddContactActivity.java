package org.briarproject.briar.android.invitation;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.invitation.InvitationListener;
import org.briarproject.bramble.api.invitation.InvitationState;
import org.briarproject.bramble.api.invitation.InvitationTask;
import org.briarproject.bramble.api.invitation.InvitationTaskFactory;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.api.android.ReferenceManager;

import java.util.logging.Logger;

import javax.inject.Inject;

import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_BLUETOOTH;
import static org.briarproject.briar.android.invitation.ConfirmationCodeView.ConfirmationState.CONNECTED;
import static org.briarproject.briar.android.invitation.ConfirmationCodeView.ConfirmationState.DETAILS;
import static org.briarproject.briar.android.invitation.ConfirmationCodeView.ConfirmationState.WAIT_FOR_CONTACT;

public class AddContactActivity extends BriarActivity
		implements InvitationListener {

	private static final Logger LOG =
			Logger.getLogger(AddContactActivity.class.getName());

	@Inject
	CryptoComponent crypto;
	@Inject
	InvitationTaskFactory invitationTaskFactory;
	@Inject
	ReferenceManager referenceManager;

	private AddContactView view = null;
	private InvitationTask task = null;
	private long taskHandle = -1;
	private AuthorId localAuthorId = null;
	private int localInvitationCode = -1, remoteInvitationCode = -1;
	private int localConfirmationCode = -1, remoteConfirmationCode = -1;
	private boolean connected = false, connectionFailed = false;
	private boolean localCompared = false, remoteCompared = false;
	private boolean localMatched = false, remoteMatched = false;
	private String contactName = null;

	// Fields that are accessed from background threads must be volatile
	@Inject
	volatile IdentityManager identityManager;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		if (state == null) {
			// This is a new activity
			setView(new ChooseIdentityView(this));
		} else {
			// Restore the activity's state
			byte[] b = state.getByteArray("briar.LOCAL_AUTHOR_ID");
			if (b != null) localAuthorId = new AuthorId(b);
			taskHandle = state.getLong("briar.TASK_HANDLE", -1);
			task = referenceManager.getReference(taskHandle,
					InvitationTask.class);

			if (task == null) {
				// No background task - we must be in an initial or final state
				localInvitationCode = state.getInt("briar.LOCAL_CODE");
				remoteInvitationCode = state.getInt("briar.REMOTE_CODE");
				connectionFailed = state.getBoolean("briar.FAILED");
				contactName = state.getString("briar.CONTACT_NAME");
				if (contactName != null) {
					localCompared = remoteCompared = true;
					localMatched = remoteMatched = true;
				}
				// Set the appropriate view for the state
				if (localInvitationCode == -1) {
					setView(new ChooseIdentityView(this));
				} else if (remoteInvitationCode == -1) {
					setView(new InvitationCodeView(this));
				} else if (connectionFailed) {
					setView(new ErrorView(this, R.string.connection_failed,
							R.string.could_not_find_contact));
				} else if (contactName == null) {
					setView(new ErrorView(this, R.string.codes_do_not_match,
							R.string.interfering));
				} else {
					showToastAndFinish();
				}
			} else {
				// A background task exists - listen to it and get its state
				InvitationState s = task.addListener(this);
				localInvitationCode = s.getLocalInvitationCode();
				remoteInvitationCode = s.getRemoteInvitationCode();
				localConfirmationCode = s.getLocalConfirmationCode();
				remoteConfirmationCode = s.getRemoteConfirmationCode();
				connected = s.getConnected();
				connectionFailed = s.getConnectionFailed();
				localCompared = s.getLocalCompared();
				remoteCompared = s.getRemoteCompared();
				localMatched = s.getLocalMatched();
				remoteMatched = s.getRemoteMatched();
				contactName = s.getContactName();
				// Set the appropriate view for the state
				if (localInvitationCode == -1) {
					setView(new ChooseIdentityView(this));
				} else if (remoteInvitationCode == -1) {
					setView(new InvitationCodeView(this));
				} else if (connectionFailed) {
					setView(new ErrorView(AddContactActivity.this,
							R.string.connection_failed,
							R.string.could_not_find_contact));
				} else if (connected && localConfirmationCode == -1) {
					setView(new ConfirmationCodeView(this, CONNECTED));
				} else if (localConfirmationCode == -1) {
					setView(new InvitationCodeView(this, true));
				} else if (!localCompared) {
					setView(new ConfirmationCodeView(this));
				} else if (!remoteCompared) {
					setView(new ConfirmationCodeView(this, WAIT_FOR_CONTACT));
				} else if (localMatched && remoteMatched) {
					if (contactName == null) {
						setView(new ConfirmationCodeView(this, DETAILS));
					} else {
						showToastAndFinish();
					}
				} else {
					setView(new ErrorView(this, R.string.codes_do_not_match,
							R.string.interfering));
				}
			}
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	private void showToastAndFinish() {
		String format = getString(R.string.contact_added_toast);
		String text = String.format(format, contactName);
		Toast.makeText(this, text, LENGTH_LONG).show();
		supportFinishAfterTransition();
	}

	@Override
	public void onStart() {
		super.onStart();
		view.populate();
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		super.onSaveInstanceState(state);
		if (localAuthorId != null) {
			byte[] b = localAuthorId.getBytes();
			state.putByteArray("briar.LOCAL_AUTHOR_ID", b);
		}
		state.putInt("briar.LOCAL_CODE", localInvitationCode);
		state.putInt("briar.REMOTE_CODE", remoteInvitationCode);
		state.putBoolean("briar.FAILED", connectionFailed);
		state.putString("briar.CONTACT_NAME", contactName);
		if (task != null) state.putLong("briar.TASK_HANDLE", taskHandle);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (task != null) task.removeListener(this);
	}

	@Override
	public void onActivityResult(int request, int result, Intent data) {
		if (request == REQUEST_BLUETOOTH) {
			if (result != RESULT_CANCELED) reset(new InvitationCodeView(this));
		}
	}

	@SuppressWarnings("ConstantConditions")
	void setView(AddContactView view) {
		this.view = view;
		view.init(this);
		setContentView(view);
		getSupportActionBar().setTitle(R.string.add_contact_title);
	}

	void reset(AddContactView view) {
		// Don't reset localAuthorId
		task = null;
		taskHandle = -1;
		localInvitationCode = -1;
		localConfirmationCode = remoteConfirmationCode = -1;
		connected = connectionFailed = false;
		localCompared = remoteCompared = false;
		localMatched = remoteMatched = false;
		contactName = null;
		setView(view);
	}

	void loadLocalAuthor() {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					LocalAuthor author = identityManager.getLocalAuthor();
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading author took " + duration + " ms");
					setLocalAuthorId(author.getId());
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	void setLocalAuthorId(final AuthorId localAuthorId) {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				AddContactActivity.this.localAuthorId = localAuthorId;
			}
		});
	}

	int getLocalInvitationCode() {
		if (localInvitationCode == -1)
			localInvitationCode = crypto.generateBTInvitationCode();
		return localInvitationCode;
	}

	int getRemoteInvitationCode() {
		return remoteInvitationCode;
	}

	void remoteInvitationCodeEntered(int code) {
		if (localAuthorId == null) throw new IllegalStateException();
		if (localInvitationCode == -1) throw new IllegalStateException();
		remoteInvitationCode = code;

		// change UI to show a progress indicator
		setView(new InvitationCodeView(this, true));

		task = invitationTaskFactory.createTask(localInvitationCode, code);
		taskHandle = referenceManager.putReference(task, InvitationTask.class);
		task.addListener(AddContactActivity.this);
		// Add a second listener so we can remove the first in onDestroy(),
		// allowing the activity to be garbage collected if it's destroyed
		task.addListener(new ReferenceCleaner(referenceManager, taskHandle));
		task.connect();
	}

	int getLocalConfirmationCode() {
		return localConfirmationCode;
	}

	void remoteConfirmationCodeEntered(int code) {
		localCompared = true;
		if (code == remoteConfirmationCode) {
			localMatched = true;
			if (remoteMatched) {
				setView(new ConfirmationCodeView(this, DETAILS));
			} else if (remoteCompared) {
				setView(new ErrorView(this, R.string.codes_do_not_match,
						R.string.interfering));
			} else {
				setView(new ConfirmationCodeView(this, WAIT_FOR_CONTACT));
			}
			task.localConfirmationSucceeded();
		} else {
			localMatched = false;
			setView(new ErrorView(this, R.string.codes_do_not_match,
					R.string.interfering));
			task.localConfirmationFailed();
		}
	}

	@Override
	public void connectionSucceeded() {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				connected = true;
				setView(new ConfirmationCodeView(AddContactActivity.this,
						CONNECTED));
			}
		});
	}

	@Override
	public void connectionFailed() {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				connectionFailed = true;
				setView(new ErrorView(AddContactActivity.this,
						R.string.connection_failed,
						R.string.could_not_find_contact));
			}
		});
	}

	@Override
	public void keyAgreementSucceeded(final int localCode,
			final int remoteCode) {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				localConfirmationCode = localCode;
				remoteConfirmationCode = remoteCode;
				setView(new ConfirmationCodeView(AddContactActivity.this));
			}
		});
	}

	@Override
	public void keyAgreementFailed() {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				connectionFailed = true;
				setView(new ErrorView(AddContactActivity.this,
						R.string.connection_failed,
						R.string.could_not_find_contact));
			}
		});
	}

	@Override
	public void remoteConfirmationSucceeded() {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				remoteCompared = true;
				remoteMatched = true;
				if (localMatched) {
					setView(new ConfirmationCodeView(AddContactActivity.this,
							DETAILS));
				}
			}
		});
	}

	@Override
	public void remoteConfirmationFailed() {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				remoteCompared = true;
				remoteMatched = false;
				if (localMatched) {
					setView(new ErrorView(AddContactActivity.this,
							R.string.codes_do_not_match, R.string.interfering));
				}
			}
		});
	}

	@Override
	public void pseudonymExchangeSucceeded(final String remoteName) {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				contactName = remoteName;
				showToastAndFinish();
			}
		});
	}

	@Override
	public void pseudonymExchangeFailed() {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				setView(new ErrorView(AddContactActivity.this,
						R.string.connection_failed,
						R.string.could_not_find_contact));
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

		@Override
		public void connectionSucceeded() {
			// Wait for key agreement to succeed or fail
		}

		@Override
		public void connectionFailed() {
			referenceManager.removeReference(handle, InvitationTask.class);
		}

		@Override
		public void keyAgreementSucceeded(int localCode, int remoteCode) {
			// Wait for remote confirmation to succeed or fail
		}

		@Override
		public void keyAgreementFailed() {
			referenceManager.removeReference(handle, InvitationTask.class);
		}

		@Override
		public void remoteConfirmationSucceeded() {
			// Wait for the pseudonym exchange to succeed or fail
		}

		@Override
		public void remoteConfirmationFailed() {
			referenceManager.removeReference(handle, InvitationTask.class);
		}

		@Override
		public void pseudonymExchangeSucceeded(String remoteName) {
			referenceManager.removeReference(handle, InvitationTask.class);
		}

		@Override
		public void pseudonymExchangeFailed() {
			referenceManager.removeReference(handle, InvitationTask.class);
		}
	}
}
