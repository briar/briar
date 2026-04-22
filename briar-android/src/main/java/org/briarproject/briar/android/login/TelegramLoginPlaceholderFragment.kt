package org.briarproject.briar.android.login

import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText
import org.briarproject.briar.R
import org.briarproject.briar.android.activity.ActivityComponent
import org.briarproject.briar.android.fragment.BaseFragment
import org.briarproject.briar.api.telegram.TelegramAuthSession.RecoverableErrorDetail
import org.briarproject.briar.api.telegram.TelegramAuthState
import org.briarproject.nullsafety.MethodsNotNullByDefault
import org.briarproject.nullsafety.ParametersNotNullByDefault
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.observe

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class TelegramLoginPlaceholderFragment : BaseFragment() {

	@Inject
	lateinit var viewModelFactory: ViewModelProvider.Factory

	private lateinit var message: TextView
	private lateinit var viewModel: StartupViewModel

	companion object {
		private const val TAG = TelegramLoginPlaceholderFragment::class.java.name

		fun newInstance(): TelegramLoginPlaceholderFragment = TelegramLoginPlaceholderFragment()
	}

	override fun injectFragment(component: ActivityComponent) {
		component.inject(this)
		viewModel = ViewModelProvider(requireActivity(), viewModelFactory)
			.get(StartupViewModel::class.java)
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		val v = inflater.inflate(R.layout.fragment_telegram_login_placeholder, container, false)

		val identifierStep = v.findViewById<View>(R.id.telegram_login_identifier_step)
		val codeEntryStep = v.findViewById<View>(R.id.telegram_login_code_step)
		val passwordEntryStep = v.findViewById<View>(R.id.telegram_login_password_step)
		val confirmationStep = v.findViewById<View>(R.id.telegram_login_confirmation)
		message = v.findViewById(R.id.message)
		val confirmationMessage = v.findViewById<TextView>(R.id.telegram_login_confirmation_message)
		val continueButton = v.findViewById<View>(R.id.btn_telegram_login_continue)
		val codeContinueButton = v.findViewById<View>(R.id.btn_telegram_login_code_continue)
		val passwordContinueButton = v.findViewById<View>(R.id.btn_telegram_login_password_continue)
		val passwordFallbackButton = v.findViewById<View>(R.id.btn_telegram_login_back)
		val identifier = v.findViewById<TextInputEditText>(R.id.telegram_login_identifier)
		val code = v.findViewById<TextInputEditText>(R.id.telegram_login_code)
		val password = v.findViewById<TextInputEditText>(R.id.telegram_login_password)

		// Initialize fields from viewModel
		identifier.setText(viewModel.telegramLoginIdentifier)
		code.setText(viewModel.telegramLoginCode)
		password.setText(viewModel.telegramLoginPassword)

		// Observe auth state changes and sync fields
		viewModel.telegramAuthState.observe(viewLifecycleOwner) { authState ->
			syncField(identifier, viewModel.telegramLoginIdentifier)
			syncField(code, viewModel.telegramLoginCode)
			syncField(password, viewModel.telegramLoginPassword)
			showCurrentStep(
				identifierStep, codeEntryStep, passwordEntryStep, confirmationStep,
				continueButton, codeContinueButton, passwordContinueButton,
				confirmationMessage, passwordFallbackButton
			)
		}

		// Identifier text watcher
		identifier.addTextChangedListener(object : android.text.TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable?) {
				viewModel.telegramLoginIdentifier = s?.toString() ?: ""
				showCurrentStep(
					identifierStep, codeEntryStep, passwordEntryStep, confirmationStep,
					continueButton, codeContinueButton, passwordContinueButton,
					confirmationMessage, passwordFallbackButton
				)
			}
		})

		// Code text watcher
		code.addTextChangedListener(object : android.text.TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable?) {
				viewModel.telegramLoginCode = s?.toString() ?: ""
				showCurrentStep(
					identifierStep, codeEntryStep, passwordEntryStep, confirmationStep,
					continueButton, codeContinueButton, passwordContinueButton,
					confirmationMessage, passwordFallbackButton
				)
			}
		})

		// Password text watcher
		password.addTextChangedListener(object : android.text.TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable?) {
				viewModel.telegramLoginPassword = s?.toString() ?: ""
				showCurrentStep(
					identifierStep, codeEntryStep, passwordEntryStep, confirmationStep,
					continueButton, codeContinueButton, passwordContinueButton,
					confirmationMessage, passwordFallbackButton
				)
			}
		})

		// Button click listeners
		v.findViewById<View>(R.id.btn_telegram_login_continue)
			.setOnClickListener { viewModel.submitTelegramLoginIdentifier() }

		v.findViewById<View>(R.id.btn_telegram_login_code_continue)
			.setOnClickListener { viewModel.submitTelegramLoginCode() }

		v.findViewById<View>(R.id.btn_telegram_login_password_continue)
			.setOnClickListener { viewModel.submitTelegramLoginPassword() }

		v.findViewById<View>(R.id.btn_telegram_login_confirmation_continue)
			.setOnClickListener { viewModel.completeTelegramLoginConfirmation() }

		v.findViewById<View>(R.id.btn_telegram_login_confirmation_back)
			.setOnClickListener { viewModel.showTelegramLoginIdentifierStep() }

		v.findViewById<View>(R.id.btn_telegram_login_back)
			.setOnClickListener { viewModel.showPasswordFragment() }

		// Initial display
		showCurrentStep(
			identifierStep, codeEntryStep, passwordEntryStep, confirmationStep,
			continueButton, codeContinueButton, passwordContinueButton,
			confirmationMessage, passwordFallbackButton
		)

		return v
	}

	private fun showCurrentStep(
		identifierStep: View,
		codeEntryStep: View,
		passwordEntryStep: View,
		confirmationStep: View,
		continueButton: View,
		codeContinueButton: View,
		passwordContinueButton: View,
		confirmationMessage: TextView,
		passwordFallbackButton: View
	) {
		val hasIdentifier = viewModel.telegramLoginIdentifier.trim().isNotEmpty()
		val hasCode = viewModel.telegramLoginCode.trim().isNotEmpty()
		val hasPassword = viewModel.telegramLoginPassword.trim().isNotEmpty()
		val authState = viewModel.telegramAuthState.value

		continueButton.isEnabled = hasIdentifier
		codeContinueButton.isEnabled = hasCode
		passwordContinueButton.isEnabled = hasPassword

		if (authState == TelegramAuthState.RECOVERABLE_ERROR &&
			viewModel.telegramRecoverableErrorDetail == RecoverableErrorDetail.MISSING_TDLIB
		) {
			continueButton.isEnabled = false
		}

		message.text = getLoginMessage(authState)
		passwordFallbackButton.visibility = View.VISIBLE

		when {
			authState == TelegramAuthState.CODE_ENTRY ||
				(authState == TelegramAuthState.RECOVERABLE_ERROR &&
					viewModel.telegramRecoverableErrorDetail == RecoverableErrorDetail.INVALID_CODE) -> {
				identifierStep.visibility = View.GONE
				codeEntryStep.visibility = View.VISIBLE
				passwordEntryStep.visibility = View.GONE
				confirmationStep.visibility = View.GONE
			}

			authState == TelegramAuthState.PASSWORD_ENTRY ||
				(authState == TelegramAuthState.RECOVERABLE_ERROR &&
					viewModel.telegramRecoverableErrorDetail == RecoverableErrorDetail.INVALID_PASSWORD) -> {
				identifierStep.visibility = View.GONE
				codeEntryStep.visibility = View.GONE
				passwordEntryStep.visibility = View.VISIBLE
				confirmationStep.visibility = View.GONE
			}

			authState == TelegramAuthState.READY -> {
				identifierStep.visibility = View.GONE
				codeEntryStep.visibility = View.GONE
				passwordEntryStep.visibility = View.GONE
				confirmationStep.visibility = View.VISIBLE
				confirmationMessage.text = getString(
					R.string.telegram_connector_login_confirmation_message,
					viewModel.telegramLoginIdentifier.trim()
				)
			}

			else -> {
				identifierStep.visibility = View.VISIBLE
				codeEntryStep.visibility = View.GONE
				passwordEntryStep.visibility = View.GONE
				confirmationStep.visibility = View.GONE
			}
		}
	}

	private fun syncField(field: TextInputEditText, value: String) {
		val current = field.text?.toString() ?: ""
		if (current != value) {
			field.setText(value)
		}
	}

	private fun getLoginMessage(authState: TelegramAuthState?): Int {
		if (authState != TelegramAuthState.RECOVERABLE_ERROR) {
			return R.string.telegram_connector_login_message
		}
		val detail = viewModel.telegramRecoverableErrorDetail
		return when (detail) {
			RecoverableErrorDetail.MISSING_TDLIB -> R.string.telegram_connector_login_tdlib_missing_message
			RecoverableErrorDetail.INVALID_IDENTIFIER -> R.string.telegram_connector_login_identifier_invalid_message
			RecoverableErrorDetail.INVALID_PASSWORD -> R.string.telegram_connector_login_password_invalid_message
			RecoverableErrorDetail.INVALID_CODE -> R.string.telegram_connector_login_code_invalid_message
			else -> R.string.telegram_connector_login_retry_message
		}
	}

	override fun onStart() {
		super.onStart()
		requireActivity().setTitle(R.string.telegram_connector_login_title)
	}

	override fun getUniqueTag(): String = TAG
}
