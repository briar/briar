package org.briarproject.briar.android.settings;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.BaseActivity;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import static java.util.Objects.requireNonNull;

@MethodsNotNullByDefault
public class ConfirmAvatarDialogFragment extends DialogFragment {

	final static String TAG = ConfirmAvatarDialogFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;
	private SettingsViewModel settingsViewModel;

	private static final String ARG_URI = "uri";
	private Uri uri;

	static ConfirmAvatarDialogFragment newInstance(Uri uri) {
		ConfirmAvatarDialogFragment f = new ConfirmAvatarDialogFragment();

		Bundle args = new Bundle();
		args.putString(ARG_URI, uri.toString());
		f.setArguments(args);

		return f;
	}

	@Override
	public void onAttach(@NonNull Context ctx) {
		super.onAttach(ctx);
		((BaseActivity) requireActivity()).getActivityComponent().inject(this);
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Bundle args = requireArguments();
		String argUri = requireNonNull(args.getString(ARG_URI));
		uri = Uri.parse(argUri);

		FragmentActivity activity = requireActivity();

		ViewModelProvider provider =
				new ViewModelProvider(activity, viewModelFactory);
		settingsViewModel = provider.get(SettingsViewModel.class);
		settingsViewModel.onCreate();

		AlertDialog.Builder builder = new AlertDialog.Builder(activity);

		LayoutInflater inflater = LayoutInflater.from(getContext());
		final View view =
				inflater.inflate(R.layout.fragment_confirm_avatar_dialog, null);
		builder.setView(view);

		builder.setTitle(R.string.dialog_confirm_profile_picture_title);
		builder.setMessage(R.string.dialog_confirm_profile_picture_question);
		builder.setNegativeButton(R.string.cancel, null);
		builder.setPositiveButton(R.string.dialog_confirm_profile_picture_set,
				(dialog, id) -> settingsViewModel.setAvatar(uri));

		ImageView imageView = view.findViewById(R.id.image);
		imageView.setImageResource(R.drawable.contact_connected);
		imageView.setImageURI(uri);

		settingsViewModel.getOwnIdentityInfo().observe(activity, us -> {
			TextView textViewUserName = view.findViewById(R.id.username);
			textViewUserName.setText(us.getLocalAuthor().getName());
		});

		return builder.create();
	}

}
