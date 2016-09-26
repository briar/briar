package org.briarproject.android.introduction;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.view.TextInputView;
import org.briarproject.api.FormatException;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.db.DbException;
import org.briarproject.api.introduction.IntroductionManager;

import java.util.logging.Logger;

import javax.inject.Inject;

import de.hdodenhof.circleimageview.CircleImageView;
import im.delight.android.identicons.IdenticonDrawable;

import static android.app.Activity.RESULT_OK;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_SHORT;
import static java.util.logging.Level.WARNING;

public class IntroductionMessageFragment extends BaseFragment
		implements TextInputView.TextInputListener {

	public final static String TAG = "IntroductionMessageFragment";

	private final static String CONTACT_ID_1 = "contact1";
	private final static String CONTACT_ID_2 = "contact2";
	private static final Logger LOG =
			Logger.getLogger(IntroductionMessageFragment.class.getName());

	private IntroductionActivity introductionActivity;
	private ViewHolder ui;
	private Contact contact1, contact2;

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile ContactManager contactManager;
	@Inject
	protected volatile IntroductionManager introductionManager;

	public static IntroductionMessageFragment newInstance(int contactId1, int contactId2) {
		Bundle args = new Bundle();
		args.putInt(CONTACT_ID_1, contactId1);
		args.putInt(CONTACT_ID_2, contactId2);
		IntroductionMessageFragment fragment =
				new IntroductionMessageFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		try {
			introductionActivity = (IntroductionActivity) context;
		} catch (ClassCastException e) {
			throw new java.lang.InstantiationError(
					"This fragment is only meant to be attached to the IntroductionActivity");
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		// change toolbar text
		ActionBar actionBar = introductionActivity.getSupportActionBar();
		if (actionBar != null) {
			actionBar.setTitle(R.string.introduction_message_title);
		}

		// inflate view
		View v = inflater.inflate(R.layout.introduction_message, container,
				false);

		// show progress bar until contacts have been loaded
		ui = new ViewHolder(v);
		ui.text.setVisibility(GONE);
		ui.message.setSendButtonEnabled(false);

		return v;
	}

	@Override
	public void onStart() {
		super.onStart();

		// get contact IDs from fragment arguments
		int contactId1 = getArguments().getInt(CONTACT_ID_1, -1);
		int contactId2 = getArguments().getInt(CONTACT_ID_2, -1);
		if (contactId1 == -1 || contactId2 == -1) {
			throw new java.lang.InstantiationError(
					"You need to use newInstance() to instantiate");
		}
		// get contacts and then show view
		prepareToSetUpViews(contactId1, contactId2);
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	private void prepareToSetUpViews(final int contactId1,
			final int contactId2) {
		introductionActivity.runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					Contact c1 = contactManager.getContact(
							new ContactId(contactId1));
					Contact c2 = contactManager.getContact(
							new ContactId(contactId2));
					setUpViews(c1, c2);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void setUpViews(final Contact c1, final Contact c2) {
		introductionActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				contact1 = c1;
				contact2 = c2;

				// set avatars
				ui.avatar1.setImageDrawable(new IdenticonDrawable(
						c1.getAuthor().getId().getBytes()));
				ui.avatar2.setImageDrawable(new IdenticonDrawable(
						c2.getAuthor().getId().getBytes()));

				// set contact names
				ui.contactName1.setText(c1.getAuthor().getName());
				ui.contactName2.setText(c2.getAuthor().getName());

				// set introduction text
				ui.text.setText(String.format(
						getString(R.string.introduction_message_text),
						c1.getAuthor().getName(), c2.getAuthor().getName()));

				// set button action
				ui.message.setListener(IntroductionMessageFragment.this);

				// hide progress bar and show views
				ui.progressBar.setVisibility(GONE);
				ui.text.setVisibility(VISIBLE);
				ui.message.setSendButtonEnabled(true);
				ui.message.showSoftKeyboard();
			}
		});
	}

	@Override
	public void onSendClick(String text) {
		// disable button to prevent accidental double invitations
		ui.message.setSendButtonEnabled(false);

		String msg = ui.message.getText().toString();
		makeIntroduction(contact1, contact2, msg);

		// don't wait for the introduction to be made before finishing activity
		introductionActivity.hideSoftKeyboard(ui.message);
		introductionActivity.setResult(RESULT_OK);
		introductionActivity.supportFinishAfterTransition();
	}

	private void makeIntroduction(final Contact c1, final Contact c2,
			final String msg) {
		introductionActivity.runOnDbThread(new Runnable() {
			@Override
			public void run() {
				// actually make the introduction
				try {
					long timestamp = System.currentTimeMillis();
					introductionManager.makeIntroduction(c1, c2, msg,
							timestamp);
				} catch (DbException | FormatException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					introductionError();
				}
			}
		});
	}

	private void introductionError() {
		introductionActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(introductionActivity,
						R.string.introduction_error, LENGTH_SHORT).show();
			}
		});
	}

	private static class ViewHolder {

		private final ProgressBar progressBar;
		private final CircleImageView avatar1, avatar2;
		private final TextView contactName1, contactName2;
		private final TextView text;
		private final TextInputView message;

		private ViewHolder(View v) {
			progressBar = (ProgressBar) v.findViewById(R.id.progressBar);
			avatar1 = (CircleImageView) v.findViewById(R.id.avatarContact1);
			avatar2 = (CircleImageView) v.findViewById(R.id.avatarContact2);
			contactName1 = (TextView) v.findViewById(R.id.nameContact1);
			contactName2 = (TextView) v.findViewById(R.id.nameContact2);
			text = (TextView) v.findViewById(R.id.introductionText);
			message = (TextInputView) v
					.findViewById(R.id.introductionMessageView);
		}
	}
}
