package org.briarproject.android.groups;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.LinearLayout.VERTICAL;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP;
import static org.briarproject.api.messaging.MessagingConstants.MAX_GROUP_NAME_LENGTH;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.contact.SelectContactsDialog;
import org.briarproject.android.invitation.AddContactActivity;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.Contact;
import org.briarproject.api.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.messaging.Group;
import org.briarproject.api.messaging.GroupFactory;
import org.briarproject.util.StringUtils;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

public class CreateGroupActivity extends BriarActivity
implements OnEditorActionListener, OnClickListener, NoContactsDialog.Listener,
SelectContactsDialog.Listener {

	private static final Logger LOG =
			Logger.getLogger(CreateGroupActivity.class.getName());

	private EditText nameEntry = null;
	private RadioGroup radioGroup = null;
	private RadioButton visibleToAll = null, visibleToSome = null;
	private Button createButton = null;
	private ProgressBar progress = null;
	private TextView feedback = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile GroupFactory groupFactory;
	@Inject private volatile DatabaseComponent db;
	private volatile Collection<Contact> contacts = null;
	private volatile Collection<ContactId> selected = Collections.emptySet();

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);
		int pad = LayoutUtils.getPadding(this);
		layout.setPadding(pad, pad, pad, pad);

		TextView chooseName = new TextView(this);
		chooseName.setGravity(CENTER);
		chooseName.setTextSize(18);
		chooseName.setText(R.string.choose_forum_name);
		layout.addView(chooseName);

		nameEntry = new EditText(this) {
			@Override
			protected void onTextChanged(CharSequence text, int start,
					int lengthBefore, int lengthAfter) {
				enableOrDisableCreateButton();
			}
		};
		nameEntry.setId(1);
		nameEntry.setMaxLines(1);
		nameEntry.setInputType(TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_CAP_SENTENCES);
		nameEntry.setOnEditorActionListener(this);
		layout.addView(nameEntry);

		radioGroup = new RadioGroup(this);
		radioGroup.setOrientation(VERTICAL);

		visibleToAll = new RadioButton(this);
		visibleToAll.setId(2);
		visibleToAll.setText(R.string.forum_visible_to_all);
		visibleToAll.setOnClickListener(this);
		radioGroup.addView(visibleToAll);

		visibleToSome = new RadioButton(this);
		visibleToSome.setId(3);
		visibleToSome.setText(R.string.forum_visible_to_some);
		visibleToSome.setOnClickListener(this);
		radioGroup.addView(visibleToSome);
		layout.addView(radioGroup);

		feedback = new TextView(this);
		feedback.setGravity(CENTER);
		feedback.setPadding(0, pad, 0, pad);
		layout.addView(feedback);

		createButton = new Button(this);
		createButton.setLayoutParams(WRAP_WRAP);
		createButton.setText(R.string.create_button);
		createButton.setOnClickListener(this);
		layout.addView(createButton);

		progress = new ProgressBar(this);
		progress.setLayoutParams(WRAP_WRAP);
		progress.setIndeterminate(true);
		progress.setVisibility(GONE);
		layout.addView(progress);

		setContentView(layout);
	}

	private void enableOrDisableCreateButton() {
		if(progress == null) return; // Not created yet
		boolean nameValid = validateName();
		boolean visibilitySelected = radioGroup.getCheckedRadioButtonId() != -1;
		createButton.setEnabled(nameValid && visibilitySelected);
	}

	public boolean onEditorAction(TextView textView, int actionId, KeyEvent e) {
		hideSoftKeyboard();
		return true;
	}

	private boolean validateName() {
		int length = StringUtils.toUtf8(nameEntry.getText().toString()).length;
		if(length > MAX_GROUP_NAME_LENGTH) {
			feedback.setText(R.string.name_too_long);
			return false;
		}
		feedback.setText("");
		return length > 0;
	}

	public void onClick(View view) {
		if(view == visibleToAll) {
			enableOrDisableCreateButton();
		} else if(view == visibleToSome) {
			if(contacts == null) loadContacts();
			else displayContacts();
		} else if(view == createButton) {
			hideSoftKeyboard();
			if(!validateName()) return;
			createButton.setVisibility(GONE);
			progress.setVisibility(VISIBLE);
			String name = nameEntry.getText().toString();
			boolean all = visibleToAll.isChecked();
			storeGroup(name, all);
		}
	}

	private void loadContacts() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					contacts = db.getContacts();
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayContacts();
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayContacts() {
		runOnUiThread(new Runnable() {
			public void run() {
				if(contacts.isEmpty()) {
					NoContactsDialog builder = new NoContactsDialog();
					builder.setListener(CreateGroupActivity.this);
					builder.build(CreateGroupActivity.this).show();
				} else {
					SelectContactsDialog builder = new SelectContactsDialog();
					builder.setListener(CreateGroupActivity.this);
					builder.setContacts(contacts);
					builder.setSelected(selected);
					builder.build(CreateGroupActivity.this).show();
				}
			}
		});
	}

	private void storeGroup(final String name, final boolean all) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					Group g = groupFactory.createGroup(name);
					long now = System.currentTimeMillis();
					db.addGroup(g);
					if(all) db.setVisibleToAll(g.getId(), true);
					else db.setVisibility(g.getId(), selected);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Storing group took " + duration + " ms");
					displayGroup(g);
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					finishOnUiThread();
				}
			}
		});
	}

	private void displayGroup(final Group g) {
		runOnUiThread(new Runnable() {
			public void run() {
				Intent i = new Intent(CreateGroupActivity.this,
						GroupActivity.class);
				i.putExtra("briar.GROUP_ID", g.getId().getBytes());
				i.putExtra("briar.GROUP_NAME", g.getName());
				startActivity(i);
				Toast.makeText(CreateGroupActivity.this,
						R.string.forum_created_toast, LENGTH_LONG).show();
				finish();
			}
		});
	}

	public void contactCreationSelected() {
		startActivity(new Intent(this, AddContactActivity.class));
	}

	public void contactCreationCancelled() {
		enableOrDisableCreateButton();
	}

	public void contactsSelected(Collection<ContactId> selected) {
		this.selected = Collections.unmodifiableCollection(selected);
		enableOrDisableCreateButton();
	}

	public void contactSelectionCancelled() {
		enableOrDisableCreateButton();
	}
}
