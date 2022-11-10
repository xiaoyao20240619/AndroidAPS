package info.nightscout.androidaps.plugins.pump.common.events

import info.nightscout.rx.events.Event

class EventBondChanged(
    var connectionAddress: String,
    var bondStatus: Boolean
) : Event()
