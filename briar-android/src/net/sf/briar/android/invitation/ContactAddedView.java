package net.sf.briar.android.invitation;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS;
import static android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import net.sf.briar.R;
import net.sf.briar.android.widgets.CommonLayoutParams;
import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class ContactAddedView extends AddContactView implements OnClickListener,
OnEditorActionListener {

	EditText nicknameEntry = null;

	ContactAddedView(Context ctx) {
		super(ctx);
	}

	void populate() {
		removeAllViews();
		Context ctx = getContext();
		LinearLayout innerLayout = new LinearLayout(ctx);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);

		ImageView icon = new ImageView(ctx);
		icon.setPadding(10, 10, 10, 10);
		icon.setImageResource(R.drawable.navigation_accept);
		innerLayout.addView(icon);

		TextView added = new TextView(ctx);
		added.setTextSize(22);
		added.setPadding(0, 10, 10, 10);
		added.setText(R.string.contact_added);
		innerLayout.addView(added);
		addView(innerLayout);

		TextView enterNickname = new TextView(ctx);
		enterNickname.setGravity(CENTER_HORIZONTAL);
		enterNickname.setPadding(10, 0, 10, 10);
		enterNickname.setText(R.string.enter_nickname);
		addView(enterNickname);

		innerLayout = new LinearLayout(ctx);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);

		final Button doneButton = new Button(ctx);
		doneButton.setLayoutParams(CommonLayoutParams.WRAP_WRAP);
		doneButton.setText(R.string.done_button);
		doneButton.setEnabled(false);
		doneButton.setOnClickListener(this);

		nicknameEntry = new EditText(ctx) {
			@Override
			protected void onTextChanged(CharSequence text, int start,
					int lengthBefore, int lengthAfter) {
				doneButton.setEnabled(text.length() > 0);
			}
		};
		nicknameEntry.setTextSize(26);
		nicknameEntry.setPadding(10, 0, 10, 10);
		nicknameEntry.setMinEms(5);
		nicknameEntry.setMaxEms(20);
		nicknameEntry.setMaxLines(1);
		nicknameEntry.setInputType(TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_CAP_WORDS |
				TYPE_TEXT_VARIATION_PERSON_NAME);
		nicknameEntry.setOnEditorActionListener(this);
		innerLayout.addView(nicknameEntry);
		innerLayout.addView(doneButton);
		addView(innerLayout);
	}

	public boolean onEditorAction(TextView textView, int actionId, KeyEvent e) {
		String nickname = textView.getText().toString();
		if(nickname.length() > 0) container.addContactAndFinish(nickname);
		return true;
	}

	public void onClick(View view) {
		String nickname = nicknameEntry.getText().toString();
		container.addContactAndFinish(nickname);
	}
}
