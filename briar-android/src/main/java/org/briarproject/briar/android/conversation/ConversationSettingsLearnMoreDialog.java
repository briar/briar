package org.briarproject.briar.android.conversation;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.briar.R;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

@MethodsNotNullByDefault
public class ConversationSettingsLearnMoreDialog extends DialogFragment {

	final static String TAG =
			ConversationSettingsLearnMoreDialog.class.getName();

	static ConversationSettingsLearnMoreDialog newInstance() {
		return new ConversationSettingsLearnMoreDialog();
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		FragmentActivity activity = requireActivity();

		AlertDialog.Builder builder =
				new AlertDialog.Builder(activity, R.style.BriarDialogTheme);

		LayoutInflater inflater = LayoutInflater.from(getContext());
		View view = inflater.inflate(
				R.layout.fragment_conversation_settings_learn_more, null);
		builder.setView(view);

		builder.setTitle(R.string.disappearing_messages_title);
		builder.setNeutralButton(R.string.ok, null);

		return builder.create();
	}


}
