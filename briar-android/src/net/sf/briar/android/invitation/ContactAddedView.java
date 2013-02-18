package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import net.sf.briar.R;
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
		icon.setImageResource(R.drawable.navigation_accept);
		icon.setPadding(10, 10, 10, 10);
		innerLayout.addView(icon);

		TextView added = new TextView(ctx);
		added.setText(R.string.contact_added);
		added.setTextSize(20);
		innerLayout.addView(added);
		addView(innerLayout);

		TextView enterNickname = new TextView(ctx);
		enterNickname.setGravity(CENTER_HORIZONTAL);
		enterNickname.setPadding(0, 0, 0, 10);
		enterNickname.setText(R.string.enter_nickname);
		addView(enterNickname);

		innerLayout = new LinearLayout(ctx);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);

		final Button done = new Button(ctx);
		nicknameEntry = new EditText(ctx) {
			@Override
			protected void onTextChanged(CharSequence text, int start,
					int lengthBefore, int lengthAfter) {
				done.setEnabled(text.length() > 0);
			}
		};
		nicknameEntry.setMinEms(10);
		nicknameEntry.setMaxEms(20);
		nicknameEntry.setMaxLines(1);
		nicknameEntry.setOnEditorActionListener(this);
		innerLayout.addView(nicknameEntry);

		done.setText(R.string.done_button);
		done.setEnabled(false);
		done.setOnClickListener(this);
		innerLayout.addView(done);
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
