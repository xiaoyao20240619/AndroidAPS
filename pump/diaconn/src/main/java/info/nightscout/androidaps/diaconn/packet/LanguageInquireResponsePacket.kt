package info.nightscout.androidaps.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.diaconn.DiaconnG8Pump
import info.nightscout.rx.logging.LTag

import javax.inject.Inject

/**
 * LanguageInquireResponsePacket
 */
class LanguageInquireResponsePacket(injector: HasAndroidInjector) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0xA0.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "LanguageInquireResponsePacket init")
    }

    override fun handleMessage(data: ByteArray?) {
        val result = defect(data)
        if (result != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "LanguageInquireResponsePacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        val result2 =  getByteToInt(bufferData)
        if(!isSuccInquireResponseResult(result2)) {
            failed = true
            return
        }
        diaconnG8Pump.selectedLanguage =  getByteToInt(bufferData)

        aapsLogger.debug(LTag.PUMPCOMM, "Result --> ${diaconnG8Pump.result}")
        aapsLogger.debug(LTag.PUMPCOMM, "selectedLanguage --> ${diaconnG8Pump.selectedLanguage}")
    }

    override fun getFriendlyName(): String {
        return "PUMP_LANGUAGE_INQUIRE_RESPONSE"
    }
}
