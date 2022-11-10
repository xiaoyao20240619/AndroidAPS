package info.nightscout.androidaps.dialogs

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.common.base.Joiner
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.DialogExtendedbolusBinding
import info.nightscout.androidaps.extensions.formatColor
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.CommandQueue
import info.nightscout.androidaps.interfaces.Constraints
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import info.nightscout.androidaps.utils.protection.ProtectionCheck.Protection.BOLUS
import info.nightscout.database.entities.UserEntry.Action
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.ui.ActivityNames
import info.nightscout.interfaces.utils.HtmlHelper
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.SafeParse
import info.nightscout.shared.interfaces.ResourceHelper
import java.text.DecimalFormat
import java.util.LinkedList
import javax.inject.Inject
import kotlin.math.abs

class ExtendedBolusDialog : DialogFragmentWithDate() {

    @Inject lateinit var ctx: Context
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var constraintChecker: Constraints
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var activityNames: ActivityNames

    private var queryingProtection = false
    private var _binding: DialogExtendedbolusBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("insulin", binding.insulin.value)
        savedInstanceState.putDouble("duration", binding.duration.value)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        onCreateViewGeneral()
        _binding = DialogExtendedbolusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pumpDescription = activePlugin.activePump.pumpDescription

        val maxInsulin = constraintChecker.getMaxExtendedBolusAllowed().value()
        val extendedStep = pumpDescription.extendedBolusStep
        binding.insulin.setParams(
            savedInstanceState?.getDouble("insulin")
                ?: extendedStep, extendedStep, maxInsulin, extendedStep, DecimalFormat("0.00"), false, binding.okcancel.ok
        )

        val extendedDurationStep = pumpDescription.extendedBolusDurationStep
        val extendedMaxDuration = pumpDescription.extendedBolusMaxDuration
        binding.duration.setParams(
            savedInstanceState?.getDouble("duration")
                ?: extendedDurationStep, extendedDurationStep, extendedMaxDuration, extendedDurationStep, DecimalFormat("0"), false, binding.okcancel.ok
        )
        binding.insulinLabel.labelFor = binding.insulin.editTextId
        binding.durationLabel.labelFor = binding.duration.editTextId
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        val insulin = SafeParse.stringToDouble(binding.insulin.text)
        val durationInMinutes = binding.duration.value.toInt()
        val actions: LinkedList<String> = LinkedList()
        val insulinAfterConstraint = constraintChecker.applyExtendedBolusConstraints(Constraint(insulin)).value()
        actions.add(rh.gs(R.string.formatinsulinunits, insulinAfterConstraint))
        actions.add(rh.gs(R.string.duration) + ": " + rh.gs(R.string.format_mins, durationInMinutes))
        if (abs(insulinAfterConstraint - insulin) > 0.01)
            actions.add(rh.gs(R.string.constraint_applied).formatColor(context, rh, R.attr.warningColor))

        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(R.string.extended_bolus), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                uel.log(
                    Action.EXTENDED_BOLUS, Sources.ExtendedBolusDialog,
                    ValueWithUnit.Insulin(insulinAfterConstraint),
                    ValueWithUnit.Minute(durationInMinutes)
                )
                commandQueue.extendedBolus(insulinAfterConstraint, durationInMinutes, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            activityNames.runAlarm(ctx, result.comment, rh.gs(R.string.treatmentdeliveryerror), R.raw.boluserror)
                        }
                    }
                })
            }, null)
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        if (!queryingProtection) {
            queryingProtection = true
            activity?.let { activity ->
                val cancelFail = {
                    queryingProtection = false
                    aapsLogger.debug(LTag.APS, "Dialog canceled on resume protection: ${this.javaClass.simpleName}")
                    ToastUtils.warnToast(ctx, R.string.dialog_canceled)
                    dismiss()
                }
                protectionCheck.queryProtection(activity, BOLUS, { queryingProtection = false }, cancelFail, cancelFail)
            }
        }
    }
}
