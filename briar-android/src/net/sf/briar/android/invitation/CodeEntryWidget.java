package net.sf.briar.android.invitation;

import static android.content.Context.INPUT_METHOD_SERVICE;
import static android.text.InputType.TYPE_CLASS_NUMBER;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.inputmethod.InputMethodManager.HIDE_IMPLICIT_ONLY;
import net.sf.briar.R;
import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class CodeEntryWidget extends LinearLayout implements
OnEditorActionListener, OnClickListener {

	private CodeEntryListener listener = null;
	private EditText codeEntry = null;

	public CodeEntryWidget(Context ctx) {
		super(ctx);
	}

	void init(CodeEntryListener listener, String prompt) {
		this.listener = listener;
		setOrientation(VERTICAL);
		setGravity(CENTER_HORIZONTAL);

		Context ctx = getContext();
		TextView enterCode = new TextView(ctx);
		enterCode.setGravity(CENTER_HORIZONTAL);
		enterCode.setPadding(0, 0, 0, 10);
		enterCode.setText(prompt);
		addView(enterCode);

		final Button continueButton = new Button(ctx);
		continueButton.setText(R.string.continue_button);
		continueButton.setEnabled(false);
		continueButton.setOnClickListener(this);

		codeEntry = new EditText(ctx) {
			@Override
			protected void onTextChanged(CharSequence text, int start,
					int lengthBefore, int lengthAfter) {
				continueButton.setEnabled(text.length() == 6);
			}
		};
		codeEntry.setOnEditorActionListener(this);
		codeEntry.setMinEms(5);
		codeEntry.setMaxEms(5);
		codeEntry.setMaxLines(1);
		codeEntry.setInputType(TYPE_CLASS_NUMBER);

		LinearLayout innerLayout = new LinearLayout(ctx);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);
		innerLayout.addView(codeEntry);
		innerLayout.addView(continueButton);
		addView(innerLayout);
	}

	public boolean onEditorAction(TextView textView, int actionId, KeyEvent e) {
		if(!validateAndReturnCode()) codeEntry.setText("");
		return true;
	}

	public void onClick(View view) {
		if(!validateAndReturnCode()) codeEntry.setText("");
	}

	private boolean validateAndReturnCode() {
		String remoteCodeString = codeEntry.getText().toString();
		int remoteCode;
		try {
			remoteCode = Integer.valueOf(remoteCodeString);
		} catch(NumberFormatException e) {
			return false;
		}
		// Hide the soft keyboard
		Object o = getContext().getSystemService(INPUT_METHOD_SERVICE);
	    ((InputMethodManager) o).toggleSoftInput(HIDE_IMPLICIT_ONLY, 0);
	    listener.codeEntered(remoteCode);
		return true;
	}
}
