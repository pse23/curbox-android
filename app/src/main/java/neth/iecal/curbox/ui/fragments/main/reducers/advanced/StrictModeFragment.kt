package neth.iecal.curbox.ui.fragments.main.reducers.advanced

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import neth.iecal.curbox.R
import neth.iecal.curbox.data.models.StrictModeConfig
import neth.iecal.curbox.data.models.StrictModeUnlockMethod
import neth.iecal.curbox.utils.DataStoreManager
import neth.iecal.curbox.utils.SettingsChangeDelayUtils
import java.util.Date

/**
 * Strict Mode locks the blockers. While it is locked, nothing can be paused, switched off,
 * deleted or loosened. It ends when its timer runs out, or, with the charger method, when
 * the user turns it off while the phone is charging.
 */
class StrictModeFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "strict_mode"
    }

    private lateinit var dataStore: DataStoreManager

    private lateinit var textStatusTitle: TextView
    private lateinit var textStatusBody: TextView
    private lateinit var groupMethod: View
    private lateinit var radioMethod: RadioGroup
    private lateinit var groupDuration: View
    private lateinit var textDurationHeader: TextView
    private lateinit var chipsDuration: ChipGroup
    private lateinit var btnPrimary: MaterialButton
    private lateinit var cardBlockUnlocks: View
    private lateinit var switchBlockUnlocks: com.google.android.material.materialswitch.MaterialSwitch

    private var config = StrictModeConfig()

    private val chipMinutes = mapOf(
        R.id.chip_strict_1h to 60L,
        R.id.chip_strict_4h to 240L,
        R.id.chip_strict_12h to 720L,
        R.id.chip_strict_1d to 1440L,
        R.id.chip_strict_3d to 4320L,
        R.id.chip_strict_7d to 10_080L,
        R.id.chip_strict_30d to 43_200L
    )

    private val ticker = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            render()
            ticker.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_strict_mode, container, false)
        dataStore = DataStoreManager(requireContext())

        textStatusTitle = view.findViewById(R.id.text_status_title)
        textStatusBody = view.findViewById(R.id.text_status_body)
        groupMethod = view.findViewById(R.id.group_method)
        radioMethod = view.findViewById(R.id.radio_method)
        groupDuration = view.findViewById(R.id.group_duration)
        textDurationHeader = view.findViewById(R.id.text_duration_header)
        chipsDuration = view.findViewById(R.id.chips_duration)
        btnPrimary = view.findViewById(R.id.btn_primary)
        cardBlockUnlocks = view.findViewById(R.id.card_block_unlocks)
        switchBlockUnlocks = view.findViewById(R.id.switch_block_unlocks)

        view.findViewById<MaterialButton>(R.id.btn_back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        radioMethod.setOnCheckedChangeListener { _, _ -> render() }
        btnPrimary.setOnClickListener { onPrimaryClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dataStore.settings.collect { settings ->
                    config = settings.strictModeConfig
                    render()
                }
            }
        }
        return view
    }

    override fun onResume() {
        super.onResume()
        ticker.post(tickRunnable)
    }

    override fun onPause() {
        super.onPause()
        ticker.removeCallbacks(tickRunnable)
    }

    override fun onDestroyView() {
        ticker.removeCallbacks(tickRunnable)
        super.onDestroyView()
    }

    private fun selectedMethod(): StrictModeUnlockMethod =
        if (radioMethod.checkedRadioButtonId == R.id.radio_charger) StrictModeUnlockMethod.CHARGER
        else StrictModeUnlockMethod.TIMER

    private fun selectedMinutes(): Long = chipMinutes[chipsDuration.checkedChipId] ?: 60L

    private fun isCharging(): Boolean {
        val battery = requireContext().registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    private fun render() {
        if (!isAdded || view == null) return
        val now = System.currentTimeMillis()
        val locked = config.isLocked(now)
        val timerLocked = locked && config.unlockMethod == StrictModeUnlockMethod.TIMER
        val chargerLocked = locked && config.unlockMethod == StrictModeUnlockMethod.CHARGER

        groupMethod.isVisible = !locked
        cardBlockUnlocks.isVisible = !locked || !config.blockUnlocks
        groupDuration.isVisible = timerLocked ||
            (!locked && selectedMethod() == StrictModeUnlockMethod.TIMER)
        textDurationHeader.setText(
            if (timerLocked) R.string.strict_mode_extend_header
            else R.string.strict_mode_duration_header
        )

        when {
            timerLocked -> {
                textStatusTitle.setText(R.string.strict_mode_status_on)
                textStatusBody.text = getString(
                    R.string.strict_mode_status_timer_body,
                    formatDeadline(config.lockedUntilMs),
                    SettingsChangeDelayUtils.formatRemaining(requireContext(), config.lockedUntilMs - now)
                )
                btnPrimary.setText(R.string.strict_mode_extend)
            }
            chargerLocked -> {
                textStatusTitle.setText(R.string.strict_mode_status_on)
                textStatusBody.setText(R.string.strict_mode_status_charger_body)
                btnPrimary.setText(R.string.strict_mode_turn_off)
            }
            else -> {
                textStatusTitle.setText(R.string.strict_mode_status_off)
                textStatusBody.setText(R.string.strict_mode_status_off_body)
                btnPrimary.setText(R.string.strict_mode_turn_on)
            }
        }
        if (locked && config.blockUnlocks) appendNoUnlocksNote()
    }

    private fun appendNoUnlocksNote() {
        textStatusBody.text = textStatusBody.text.toString() + " " +
            getString(R.string.strict_mode_status_no_unlocks)
    }

    private fun formatDeadline(untilMs: Long): String {
        val date = Date(untilMs)
        return DateFormat.getMediumDateFormat(requireContext()).format(date) + " " +
            DateFormat.getTimeFormat(requireContext()).format(date)
    }

    private fun onPrimaryClicked() {
        val now = System.currentTimeMillis()
        val locked = config.isLocked(now)
        when {
            locked && config.unlockMethod == StrictModeUnlockMethod.CHARGER -> turnOff()
            locked -> extend()
            else -> confirmTurnOn()
        }
    }

    private fun confirmTurnOn() {
        val method = selectedMethod()
        val minutes = selectedMinutes()
        val blockUnlocks = switchBlockUnlocks.isChecked
        val message = if (method == StrictModeUnlockMethod.TIMER) {
            getString(
                R.string.strict_mode_confirm_timer,
                formatDeadline(System.currentTimeMillis() + minutes * 60_000L)
            )
        } else {
            getString(R.string.strict_mode_confirm_charger)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.strict_mode_confirm_title)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.strict_mode_turn_on) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    dataStore.startStrictMode(method, minutes, blockUnlocks)
                }
            }
            .show()
    }

    private fun extend() {
        val minutes = selectedMinutes()
        viewLifecycleOwner.lifecycleScope.launch {
            val extended = dataStore.startStrictMode(
                StrictModeUnlockMethod.TIMER,
                minutes,
                switchBlockUnlocks.isChecked
            )
            if (!extended && isAdded) {
                Toast.makeText(requireContext(), R.string.strict_mode_extend_shorter, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun turnOff() {
        val charging = isCharging()
        viewLifecycleOwner.lifecycleScope.launch {
            val stopped = dataStore.stopStrictMode(charging)
            if (!stopped && isAdded) {
                Toast.makeText(requireContext(), R.string.strict_mode_needs_charger, Toast.LENGTH_LONG).show()
            }
        }
    }
}
