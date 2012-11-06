package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.widget.LinearLayout.HORIZONTAL;
import net.sf.briar.R;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class ContactAddedActivity extends Activity implements OnClickListener,
OnEditorActionListener {

	private volatile Button done = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_contact_added);
		LinearLayout outerLayout = (LinearLayout) findViewById(
				R.id.contact_added_container);

		LinearLayout innerLayout = new LinearLayout(this);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);
		ImageView icon = new ImageView(this);
		icon.setImageResource(R.drawable.iconic_check_alt_green);
		icon.setPadding(10, 10, 10, 10);
		innerLayout.addView(icon);
		TextView failed = new TextView(this);
		failed.setTextSize(20);
		failed.setText(R.string.contact_added);
		innerLayout.addView(failed);
		outerLayout.addView(innerLayout);

		TextView enterNickname = new TextView(this);
		enterNickname.setGravity(CENTER_HORIZONTAL);
		enterNickname.setText(R.string.enter_nickname);
		outerLayout.addView(enterNickname);
		final Button addAnother = new Button(this);
		final Button done = new Button(this);
		this.done = done;
		EditText nicknameEntry = new EditText(this) {
			@Override
			protected void onTextChanged(CharSequence text, int start,
					int lengthBefore, int lengthAfter) {
				addAnother.setEnabled(text.length() > 0);
				done.setEnabled(text.length() > 0);
			}
		};
		nicknameEntry.setMinEms(10);
		nicknameEntry.setMaxEms(20);
		nicknameEntry.setMaxLines(1);
		nicknameEntry.setOnEditorActionListener(this);
		outerLayout.addView(nicknameEntry);

		innerLayout = new LinearLayout(this);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);
		addAnother.setText(R.string.add_another_contact_button);
		addAnother.setEnabled(false);
		addAnother.setOnClickListener(this);
		innerLayout.addView(addAnother);
		done.setText(R.string.done_button);
		done.setEnabled(false);
		done.setOnClickListener(this);
		innerLayout.addView(done);
		outerLayout.addView(innerLayout);
	}

	public boolean onEditorAction(TextView textView, int actionId, KeyEvent e) {
		if(textView.getText().length() > 0) finish();
		return true;
	}

	public void onClick(View view) {
		if(done == null) return;
		if(view != done)
			startActivity(new Intent(this, NetworkSetupActivity.class));
		finish();
	}
}
