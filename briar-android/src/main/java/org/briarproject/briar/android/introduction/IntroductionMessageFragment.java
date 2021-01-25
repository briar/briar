package org.briarproject.briar.android.introduction;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.contact.ContactItem;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.view.TextInputView;
import org.briarproject.briar.android.view.TextSendController;
import org.briarproject.briar.android.view.TextSendController.SendListener;
import org.briarproject.briar.api.attachment.AttachmentHeader;
import org.briarproject.briar.api.identity.AuthorInfo;
import org.briarproject.briar.api.identity.AuthorManager;
import org.briarproject.briar.api.introduction.IntroductionManager;

import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import de.hdodenhof.circleimageview.CircleImageView;

import static android.app.Activity.RESULT_OK;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_SHORT;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.android.util.UiUtils.getContactDisplayName;
import static org.briarproject.briar.android.util.UiUtils.hideSoftKeyboard;
import static org.briarproject.briar.android.view.AuthorView.setAvatar;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MAX_INTRODUCTION_TEXT_LENGTH;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class IntroductionMessageFragment extends BaseFragment
		implements SendListener {

	public static final String TAG =
			IntroductionMessageFragment.class.getName();
	private static final Logger LOG = Logger.getLogger(TAG);

	private final static String CONTACT_ID_1 = "contact1";
	private final static String CONTACT_ID_2 = "contact2";

	private IntroductionActivity introductionActivity;
	private ViewHolder ui;
	private Contact contact1, contact2;

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile ContactManager contactManager;
	@Inject
	protected volatile AuthorManager authorManager;
	@Inject
	protected volatile IntroductionManager introductionManager;

	public static IntroductionMessageFragment newInstance(int contactId1,
			int contactId2) {
		Bundle args = new Bundle();
		args.putInt(CONTACT_ID_1, contactId1);
		args.putInt(CONTACT_ID_2, contactId2);
		IntroductionMessageFragment fragment =
				new IntroductionMessageFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		introductionActivity = (IntroductionActivity) context;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {

		// change toolbar text
		ActionBar actionBar = introductionActivity.getSupportActionBar();
		if (actionBar != null) {
			actionBar.setTitle(R.string.introduction_message_title);
		}

		// get contact IDs from fragment arguments
		Bundle args = requireArguments();
		int contactId1 = args.getInt(CONTACT_ID_1, -1);
		int contactId2 = args.getInt(CONTACT_ID_2, -1);
		if (contactId1 == -1 || contactId2 == -1) {
			throw new AssertionError("Use newInstance() to instantiate");
		}

		// inflate view
		View v = inflater.inflate(R.layout.introduction_message, container,
				false);
		ui = new ViewHolder(v);
		TextSendController sendController =
				new TextSendController(ui.message, this, true);
		ui.message.setSendController(sendController);
		ui.message.setMaxTextLength(MAX_INTRODUCTION_TEXT_LENGTH);
		ui.message.setReady(false);

		// get contacts and then show view
		prepareToSetUpViews(contactId1, contactId2);

		return v;
	}

	@Override
	public void onStart() {
		super.onStart();
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	private void prepareToSetUpViews(int contactId1, int contactId2) {
		introductionActivity.runOnDbThread(() -> {
			try {
				Contact contact1 =
						contactManager.getContact(new ContactId(contactId1));
				Contact contact2 =
						contactManager.getContact(new ContactId(contactId2));
				AuthorInfo a1 = authorManager.getAuthorInfo(contact1);
				AuthorInfo a2 = authorManager.getAuthorInfo(contact2);
				boolean possible =
						introductionManager.canIntroduce(contact1, contact2);
				ContactItem c1 = new ContactItem(contact1, a1);
				ContactItem c2 = new ContactItem(contact2, a2);
				setUpViews(c1, c2, possible);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	private void setUpViews(ContactItem c1, ContactItem c2, boolean possible) {
		introductionActivity.runOnUiThreadUnlessDestroyed(() -> {
			contact1 = c1.getContact();
			contact2 = c2.getContact();

			// set avatars
			setAvatar(ui.avatar1, c1);
			setAvatar(ui.avatar2, c2);

			// set contact names
			ui.contactName1.setText(getContactDisplayName(c1.getContact()));
			ui.contactName2.setText(getContactDisplayName(c2.getContact()));

			// hide progress bar
			ui.progressBar.setVisibility(GONE);

			if (possible) {
				// show views
				ui.notPossible.setVisibility(GONE);
				ui.message.setVisibility(VISIBLE);
				ui.message.setReady(true);
				ui.message.showSoftKeyboard();
			} else {
				ui.notPossible.setVisibility(VISIBLE);
				ui.message.setVisibility(GONE);
			}
		});
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				hideSoftKeyboard(ui.message);
				introductionActivity.onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onSendClick(@Nullable String text,
			List<AttachmentHeader> headers) {
		// disable button to prevent accidental double invitations
		ui.message.setReady(false);

		makeIntroduction(contact1, contact2, text);

		// don't wait for the introduction to be made before finishing activity
		hideSoftKeyboard(ui.message);
		introductionActivity.setResult(RESULT_OK);
		introductionActivity.supportFinishAfterTransition();
	}

	private void makeIntroduction(Contact c1, Contact c2,
			@Nullable String text) {
		introductionActivity.runOnDbThread(() -> {
			// actually make the introduction
			try {
				long timestamp = System.currentTimeMillis();
				introductionManager.makeIntroduction(c1, c2, text, timestamp);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				introductionError();
			}
		});
	}

	private void introductionError() {
		introductionActivity.runOnUiThreadUnlessDestroyed(
				() -> Toast.makeText(introductionActivity,
						R.string.introduction_error, LENGTH_SHORT).show());
	}

	private static class ViewHolder {

		private final ProgressBar progressBar;
		private final CircleImageView avatar1, avatar2;
		private final TextView contactName1, contactName2;
		private final TextView notPossible;
		private final TextInputView message;

		private ViewHolder(View v) {
			progressBar = v.findViewById(R.id.progressBar);
			avatar1 = v.findViewById(R.id.avatarContact1);
			avatar2 = v.findViewById(R.id.avatarContact2);
			contactName1 = v.findViewById(R.id.nameContact1);
			contactName2 = v.findViewById(R.id.nameContact2);
			notPossible = v.findViewById(R.id.introductionNotPossibleView);
			message = v.findViewById(R.id.introductionMessageView);
		}
	}
}
