package org.briarproject.briar.android.login;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.api.telegram.TelegramAuthSession.RecoverableErrorDetail;
import org.briarproject.briar.api.telegram.TelegramAuthState;
import org.briarproject.nullsafety.MethodsNotNullByDefault;
import org.briarproject.nullsafety.ParametersNotNullByDefault;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.lifecycle.ViewModelProvider;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class TelegramLoginPlaceholderFragment extends BaseFragment {

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private TextView message;
	private StartupViewModel viewModel;

	static final String TAG =
			TelegramLoginPlaceholderFragment.class.getName();

	static TelegramLoginPlaceholderFragment newInstance() {
		return new TelegramLoginPlaceholderFragment();
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(requireActivity(),
				viewModelFactory).get(StartupViewModel.class);
	}

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_telegram_login_placeholder,
				container, false);
		View identifierStep = v.findViewById(R.id.telegram_login_identifier_step);
		View codeEntryStep = v.findViewById(R.id.telegram_login_code_step);
		View passwordEntryStep =
				v.findViewById(R.id.telegram_login_password_step);
		View confirmationStep = v.findViewById(R.id.telegram_login_confirmation);
		TextView message = v.findViewById(R.id.message);
		TextView confirmationMessage =
				v.findViewById(R.id.telegram_login_confirmation_message);
		View continueButton = v.findViewById(R.id.btn_telegram_login_continue);
		View codeContinueButton =
				v.findViewById(R.id.btn_telegram_login_code_continue);
		View passwordContinueButton =
				v.findViewById(R.id.btn_telegram_login_password_continue);
		View passwordFallbackButton = v.findViewById(R.id.btn_telegram_login_back);
		TextInputEditText identifier =
				v.findViewById(R.id.telegram_login_identifier);
		TextInputEditText code =
				v.findViewById(R.id.telegram_login_code);
		TextInputEditText password =
				v.findViewById(R.id.telegram_login_password);
		this.message = message;
		identifier.setText(viewModel.getTelegramLoginIdentifier());
		code.setText(viewModel.getTelegramLoginCode());
		password.setText(viewModel.getTelegramLoginPassword());
		viewModel.getTelegramAuthState().observe(getViewLifecycleOwner(),
				authState -> showCurrentStep(identifierStep, codeEntryStep,
						passwordEntryStep, confirmationStep, continueButton,
						codeContinueButton, passwordContinueButton,
						confirmationMessage, passwordFallbackButton));
		identifier.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				viewModel.setTelegramLoginIdentifier(s.toString());
				showCurrentStep(identifierStep, codeEntryStep, passwordEntryStep,
						confirmationStep, continueButton, codeContinueButton,
						passwordContinueButton, confirmationMessage,
						passwordFallbackButton);
			}
		});
		code.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				viewModel.setTelegramLoginCode(s.toString());
				showCurrentStep(identifierStep, codeEntryStep, passwordEntryStep,
						confirmationStep, continueButton, codeContinueButton,
						passwordContinueButton, confirmationMessage,
						passwordFallbackButton);
			}
		});
		password.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				viewModel.setTelegramLoginPassword(s.toString());
				showCurrentStep(identifierStep, codeEntryStep, passwordEntryStep,
						confirmationStep, continueButton, codeContinueButton,
						passwordContinueButton, confirmationMessage,
						passwordFallbackButton);
			}
		});
		v.findViewById(R.id.btn_telegram_login_continue)
				.setOnClickListener(view -> {
					viewModel.submitTelegramLoginIdentifier();
				});
		v.findViewById(R.id.btn_telegram_login_code_continue)
				.setOnClickListener(view -> {
					viewModel.submitTelegramLoginCode();
				});
		v.findViewById(R.id.btn_telegram_login_password_continue)
				.setOnClickListener(view -> {
					viewModel.submitTelegramLoginPassword();
				});
		v.findViewById(R.id.btn_telegram_login_confirmation_continue)
				.setOnClickListener(view -> {
					viewModel.completeTelegramLoginConfirmation();
				});
		v.findViewById(R.id.btn_telegram_login_confirmation_back)
				.setOnClickListener(view -> {
					viewModel.showTelegramLoginIdentifierStep();
				});
		v.findViewById(R.id.btn_telegram_login_back)
				.setOnClickListener(view -> viewModel.showPasswordFragment());
		showCurrentStep(identifierStep, codeEntryStep, passwordEntryStep,
				confirmationStep, continueButton, codeContinueButton,
				passwordContinueButton, confirmationMessage,
				passwordFallbackButton);
		return v;
	}

	private void showCurrentStep(View identifierStep, View codeEntryStep,
			View passwordEntryStep, View confirmationStep, View continueButton,
			View codeContinueButton, View passwordContinueButton,
			TextView confirmationMessage,
			View passwordFallbackButton) {
		boolean hasIdentifier =
				!viewModel.getTelegramLoginIdentifier().trim().isEmpty();
		boolean hasCode = !viewModel.getTelegramLoginCode().trim().isEmpty();
		boolean hasPassword =
				!viewModel.getTelegramLoginPassword().trim().isEmpty();
		TelegramAuthState authState = viewModel.getTelegramAuthState().getValue();
		continueButton.setEnabled(hasIdentifier);
		codeContinueButton.setEnabled(hasCode);
		passwordContinueButton.setEnabled(hasPassword);
		message.setText(getLoginMessage(authState));
		passwordFallbackButton.setVisibility(View.VISIBLE);
		if (authState == TelegramAuthState.CODE_ENTRY) {
			identifierStep.setVisibility(View.GONE);
			codeEntryStep.setVisibility(View.VISIBLE);
			passwordEntryStep.setVisibility(View.GONE);
			confirmationStep.setVisibility(View.GONE);
		} else if (authState == TelegramAuthState.PASSWORD_ENTRY ||
				authState == TelegramAuthState.RECOVERABLE_ERROR &&
						viewModel.getTelegramRecoverableErrorDetail()
						== RecoverableErrorDetail.INVALID_PASSWORD) {
			identifierStep.setVisibility(View.GONE);
			codeEntryStep.setVisibility(View.GONE);
			passwordEntryStep.setVisibility(View.VISIBLE);
			confirmationStep.setVisibility(View.GONE);
		} else if (authState == TelegramAuthState.READY) {
			identifierStep.setVisibility(View.GONE);
			codeEntryStep.setVisibility(View.GONE);
			passwordEntryStep.setVisibility(View.GONE);
			confirmationStep.setVisibility(View.VISIBLE);
			confirmationMessage.setText(getString(
					R.string.telegram_connector_login_confirmation_message,
					viewModel.getTelegramLoginIdentifier()));
		} else {
			identifierStep.setVisibility(View.VISIBLE);
			codeEntryStep.setVisibility(View.GONE);
			passwordEntryStep.setVisibility(View.GONE);
			confirmationStep.setVisibility(View.GONE);
		}
	}

	private int getLoginMessage(TelegramAuthState authState) {
		if (authState != TelegramAuthState.RECOVERABLE_ERROR) {
			return R.string.telegram_connector_login_message;
		}
		RecoverableErrorDetail detail =
				viewModel.getTelegramRecoverableErrorDetail();
		if (detail == RecoverableErrorDetail.MISSING_TDLIB) return R.string.telegram_connector_login_tdlib_missing_message;
		if (detail == RecoverableErrorDetail.INVALID_IDENTIFIER) return R.string.telegram_connector_login_identifier_invalid_message;
		if (detail == RecoverableErrorDetail.INVALID_PASSWORD) return R.string.telegram_connector_login_password_invalid_message;
		return detail == RecoverableErrorDetail.INVALID_CODE
				? R.string.telegram_connector_login_code_invalid_message
				: R.string.telegram_connector_login_retry_message;
	}

	@Override
	public void onStart() {
		super.onStart();
		requireActivity().setTitle(R.string.telegram_connector_login_title);
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}
}
