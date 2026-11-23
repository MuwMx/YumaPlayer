/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.audio.pass

internal object UsbAudioDescriptors {

    data class Plan(
        val controlInterface: Int,
        val streamingInterface: Int,
        val alt: Int,
        val uac2: Boolean,
        val clockId: Int,
        val selectorId: Int,
        val selectorPin: Int,
        val endpoint: Int,
        val endpointAttributes: Int,
        val maxPacket: Int,
        val interval: Int,
        val rateControl: Boolean,
        val feedbackEndpoint: Int,
        val feedbackMaxPacket: Int,
        val rate: Int,
        val channels: Int,
        val subslot: Int,
        val bitResolution: Int,
    )

    data class VolumeUnit(val controlInterface: Int, val unitId: Int, val uac2: Boolean, val channels: List<Int>)

    data class Dac(val bcdUsb: Int, val settings: List<Setting>, val volume: VolumeUnit?)

    data class Setting(
        val controlInterface: Int,
        val streamingInterface: Int,
        val alt: Int,
        val uac2: Boolean,
        val terminalLink: Int,
        val channels: Int,
        val subslot: Int,
        val bitResolution: Int,
        val rates: List<Pair<Int, Int>>,
        val endpoint: Endpoint?,
        val feedback: Endpoint?,
        val clockId: Int = 0,
        val selectorId: Int = 0,
        val selectorPin: Int = 0,
    )

    data class Endpoint(val address: Int, val attributes: Int, val maxPacket: Int, val interval: Int, val syncAddress: Int, val rateControl: Boolean)

    private class Entity(val id: Int, val subtype: Int, val source: List<Int>, val clock: Int, val terminalType: Int, val volumeChannels: List<Int>)

    fun parse(bytes: ByteArray): Dac {
        var bcdUsb = 0
        var iface = -1
        var alt = 0
        var ifaceClass = 0
        var ifaceSub = 0
        var protocol = 0
        var control = -1
        val entities = HashMap<Pair<Int, Int>, Entity>()
        val settings = mutableListOf<Setting>()
        var current: Setting? = null
        var endpoints = mutableListOf<Endpoint>()

        fun finish() {
            val setting = current ?: return
            val data = endpoints.firstOrNull { it.address and 0x80 == 0 && it.attributes and 3 == 1 }
            val feedback = endpoints.firstOrNull { ep ->
                ep.address and 0x80 != 0 && ep.attributes and 3 == 1 &&
                    ((ep.attributes shr 4) and 3 == 1 || (data != null && ep.address == data.syncAddress))
            }
            settings += setting.copy(endpoint = data, feedback = feedback)
            current = null
            endpoints = mutableListOf()
        }

        var offset = 0
        while (offset + 2 <= bytes.size) {
            val length = bytes[offset].toInt() and 0xff
            if (length < 2 || offset + length > bytes.size) break
            val d = bytes.copyOfRange(offset, offset + length)
            offset += length
            fun u(i: Int) = if (i < d.size) d[i].toInt() and 0xff else 0
            fun u16(i: Int) = u(i) or (u(i + 1) shl 8)
            fun u24(i: Int) = u16(i) or (u(i + 2) shl 16)
            when (u(1)) {
                0x01 -> bcdUsb = u16(2)
                0x04 -> {
                    finish()
                    iface = u(2)
                    alt = u(3)
                    ifaceClass = u(5)
                    ifaceSub = u(6)
                    protocol = u(7)
                    if (ifaceClass == 1 && ifaceSub == 1) control = iface
                    if (ifaceClass == 1 && ifaceSub == 2 && alt > 0 && control >= 0) {
                        current = Setting(control, iface, alt, protocol == 0x20, 0, 0, 0, 0, emptyList(), null, null)
                    }
                }
                0x05 -> if (current != null && length >= 7) {
                    val packet = u16(4)
                    endpoints += Endpoint(
                        address = u(2),
                        attributes = u(3),
                        maxPacket = (packet and 0x7ff) * (1 + ((packet shr 11) and 3)),
                        interval = u(6),
                        syncAddress = if (length >= 9) u(8) else 0,
                        rateControl = false,
                    )
                }
                0x25 -> if (current != null && length >= 4 && u(2) == 1 && endpoints.isNotEmpty() && current?.uac2 == false) {
                    val last = endpoints.removeAt(endpoints.lastIndex)
                    endpoints += last.copy(rateControl = u(3) and 1 != 0)
                }
                0x24 -> when {
                    ifaceClass == 1 && ifaceSub == 1 -> controlEntity(d, protocol == 0x20)?.let { entities[iface to it.id] = it }
                    current != null -> current = streamingEntity(current!!, d, ::u, ::u24)
                }
            }
        }
        finish()

        val pcm = settings
            .filter { it.endpoint != null && it.channels in 1..2 && it.subslot in 2..4 && it.bitResolution in 16..32 }
            .map { s -> if (s.uac2) withClock(s, entities) else s }
        return Dac(bcdUsb, pcm, volumeUnit(pcm, entities))
    }

    private fun controlEntity(d: ByteArray, uac2: Boolean): Entity? {
        fun u(i: Int) = if (i < d.size) d[i].toInt() and 0xff else 0
        fun u16(i: Int) = u(i) or (u(i + 1) shl 8)
        val subtype = u(2)
        return when (subtype) {
            0x02 -> Entity(u(3), subtype, emptyList(), if (uac2) u(7) else 0, u16(4), emptyList())
            0x03 -> Entity(u(3), subtype, listOf(u(7)), if (uac2) u(8) else 0, u16(4), emptyList())
            0x04 -> Entity(u(3), subtype, (0 until u(4)).map { u(5 + it) }, 0, 0, emptyList())
            0x05 -> Entity(u(3), subtype, (0 until u(4)).map { u(5 + it) }, 0, 0, emptyList())
            0x06 -> Entity(u(3), subtype, listOf(u(4)), 0, 0, featureVolume(d, uac2))
            0x0a -> if (uac2) Entity(u(3), subtype, emptyList(), 0, 0, emptyList()) else null
            0x0b -> if (uac2) Entity(u(3), subtype, (0 until u(4)).map { u(5 + it) }, 0, 0, emptyList()) else null
            0x0c -> if (uac2) Entity(u(3), subtype, listOf(u(4)), 0, 0, emptyList()) else null
            else -> null
        }
    }

    private fun featureVolume(d: ByteArray, uac2: Boolean): List<Int> {
        fun u(i: Int) = if (i < d.size) d[i].toInt() and 0xff else 0
        val size = if (uac2) 4 else u(5)
        val start = if (uac2) 5 else 6
        if (size == 0) return emptyList()
        val count = (d.size - start - 1) / size
        return (0 until count).filter { ch ->
            val first = u(start + ch * size)
            if (uac2) (first shr 2) and 3 == 3 else first and 0x02 != 0
        }
    }

    private fun streamingEntity(s: Setting, d: ByteArray, u: (Int) -> Int, u24: (Int) -> Int): Setting = when {
        u(2) == 1 && s.uac2 -> if (u(5) == 1 && (u(6) and 1) != 0) s.copy(terminalLink = u(3), channels = u(10)) else s.copy(terminalLink = u(3), channels = 0)
        u(2) == 1 -> if ((u(5) or (u(6) shl 8)) == 1) s.copy(terminalLink = u(3)) else s.copy(terminalLink = u(3), subslot = -1)
        u(2) == 2 && u(3) == 1 && s.uac2 -> s.copy(subslot = u(4), bitResolution = u(5))
        u(2) == 2 && u(3) == 1 && s.subslot >= 0 -> {
            val count = u(7)
            val rates = if (count == 0) listOf(u24(8) to u24(11)) else (0 until count).map { val r = u24(8 + it * 3); r to r }
            s.copy(channels = u(4), subslot = u(5), bitResolution = u(6), rates = rates)
        }
        else -> s
    }

    private fun withClock(s: Setting, entities: Map<Pair<Int, Int>, Entity>): Setting {
        var id = entities[s.controlInterface to s.terminalLink]?.clock ?: 0
        var selector = 0
        var pin = 0
        for (step in 0 until 4) {
            val entity = entities[s.controlInterface to id] ?: break
            when (entity.subtype) {
                0x0b -> {
                    val index = entity.source.indexOfFirst { src -> entities[s.controlInterface to src]?.subtype == 0x0a }.coerceAtLeast(0)
                    selector = entity.id
                    pin = index + 1
                    id = entity.source.getOrElse(index) { 0 }
                }
                0x0c -> id = entity.source.firstOrNull() ?: 0
                else -> break
            }
        }
        val isSource = entities[s.controlInterface to id]?.subtype == 0x0a
        return s.copy(clockId = if (isSource) id else 0, selectorId = selector, selectorPin = pin)
    }

    private fun volumeUnit(settings: List<Setting>, entities: Map<Pair<Int, Int>, Entity>): VolumeUnit? {
        val setting = settings.firstOrNull() ?: return null
        val control = setting.controlInterface
        val outputs = entities.values.filter { it.subtype == 0x03 && it.terminalType != 0x0101 && entities[control to it.id] === it }
        val found = mutableSetOf<VolumeUnit>()
        for (output in outputs) {
            var id = output.source.firstOrNull() ?: continue
            var unit: VolumeUnit? = null
            val seen = mutableSetOf<Int>()
            while (id != setting.terminalLink && seen.add(id)) {
                val entity = entities[control to id] ?: break
                if (unit == null && entity.subtype == 0x06 && entity.volumeChannels.isNotEmpty()) {
                    val channels = if (0 in entity.volumeChannels) listOf(0) else entity.volumeChannels.filter { it in 1..setting.channels }
                    unit = VolumeUnit(control, entity.id, setting.uac2, channels)
                }
                if (entity.source.size != 1) break
                id = entity.source.first()
            }
            if (id == setting.terminalLink && unit != null) found += unit
        }
        return found.singleOrNull()
    }

    fun plans(dac: Dac, rate: Int, channels: Int, sourceBits: Int): List<Plan> =
        dac.settings
            .filter { it.channels == channels && it.bitResolution >= sourceBits && it.subslot * 8 >= it.bitResolution }
            .filter { s -> s.uac2 || s.rates.any { (low, high) -> rate in low..high } }
            .sortedWith(compareBy({ it.subslot }, { it.bitResolution }))
            .mapNotNull { s -> planFor(s, rate) }

    private fun planFor(s: Setting, rate: Int): Plan? {
        val data = s.endpoint ?: return null
        if (data.interval !in 1..4 || data.maxPacket == 0) return null
        if ((data.attributes shr 2) and 3 == 1 && s.feedback == null) return null
        if (!s.uac2 && !data.rateControl && s.rates != listOf(rate to rate)) return null
        if (s.uac2 && s.clockId == 0) return null
        return Plan(
            controlInterface = s.controlInterface,
            streamingInterface = s.streamingInterface,
            alt = s.alt,
            uac2 = s.uac2,
            clockId = s.clockId,
            selectorId = s.selectorId,
            selectorPin = s.selectorPin,
            endpoint = data.address,
            endpointAttributes = data.attributes,
            maxPacket = data.maxPacket,
            interval = data.interval,
            rateControl = data.rateControl,
            feedbackEndpoint = s.feedback?.address ?: -1,
            feedbackMaxPacket = s.feedback?.maxPacket ?: 0,
            rate = rate,
            channels = s.channels,
            subslot = s.subslot,
            bitResolution = s.bitResolution,
        )
    }
}
