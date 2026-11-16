package me.jhot.swiftcurrent.core

import android.util.Log
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64

/** What an item does when tapped. Widgets are out of version one, but a new subtype slots in here. */
@Serializable
sealed interface Target {
    @Serializable
    data class App(val component: String) : Target

    @Serializable
    data class Shortcut(val key: String) : Target

    /** A folder has no launch target of its own: tapping it opens its [Entry.popup]. */
    @Serializable
    data object Folder : Target
}

/** One item, shared by the favorites list and by pop-ups. */
@Serializable
data class Entry(
    val id: String,
    val target: Target,
    val customLabel: String? = null,
    val popup: Popup? = null,
    /** Home shows this favorite only while all of these hold. Defaulted so older data and backups decode. */
    val conditions: List<Condition> = emptyList(),
)

@Serializable
data class Popup(val rows: List<PopupRow>)

@Serializable
sealed interface PopupRow {
    /** The app's own shortcuts, kept as one row so it can be moved or left out. */
    @Serializable
    data class OwnShortcuts(val app: String) : PopupRow

    @Serializable
    data class Item(val entry: Entry) : PopupRow
}

/**
 * Icon bytes as a Base64 string: the default byte-array form is a JSON number array, 3 to 4 times
 * larger. The decoder still accepts that array so data written before this change loads, which is
 * why backups stay version 1. A malformed icon decodes to null (logged): losing one icon must never
 * make the whole store unreadable, which would blank the owner's favorites and settings.
 */
object Base64ByteArraySerializer : KSerializer<ByteArray?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Base64Bytes", PrimitiveKind.STRING).nullable

    override fun serialize(encoder: Encoder, value: ByteArray?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(Base64.getEncoder().encodeToString(value))
    }

    override fun deserialize(decoder: Decoder): ByteArray? {
        val json = decoder as? JsonDecoder ?: return decodeText(decoder.decodeString())
        return when (val e = json.decodeJsonElement()) {
            JsonNull -> null
            is JsonArray -> {
                val out = ByteArray(e.size)
                for (i in e.indices) {
                    val n = (e[i] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()
                    if (n == null || n !in -128..255) return lenient("bad element at $i")
                    out[i] = n.toByte()
                }
                out
            }
            is JsonPrimitive -> if (e.isString) decodeText(e.content) else lenient("not text or an array")
            else -> lenient("wrong type")
        }
    }

    private fun decodeText(text: String): ByteArray? = try {
        Base64.getDecoder().decode(text)
    } catch (ex: IllegalArgumentException) {
        lenient("not valid Base64")
    }

    private fun lenient(why: String): ByteArray? {
        // Plain JVM tests have no Log implementation; a logging failure must not undo the leniency.
        try {
            Log.w("UserShortcut", "dropping a stored icon: $why")
        } catch (_: RuntimeException) {
        }
        return null
    }
}

@Serializable
data class UserShortcut(
    val id: String,
    val appPackage: String,
    val label: String,
    val intentUri: String,
    @Serializable(with = Base64ByteArraySerializer::class)
    val iconBytes: ByteArray? = null,
) {
    // Generated equality compares the array by reference, which would break backup round-trips.
    override fun equals(other: Any?): Boolean =
        other is UserShortcut && id == other.id && appPackage == other.appPackage &&
            label == other.label && intentUri == other.intentUri &&
            iconBytes.contentEquals(other.iconBytes)

    override fun hashCode(): Int {
        var h = id.hashCode()
        h = 31 * h + appPackage.hashCode()
        h = 31 * h + label.hashCode()
        h = 31 * h + intentUri.hashCode()
        return 31 * h + iconBytes.contentHashCode()
    }
}

/** How strong a haptic tick is, as a percentage of the vibrator's full amplitude: 0 is no tick at all. */
object HapticStrength {
    const val MIN = 0
    const val MAX = 100
    const val DEFAULT = 20
    const val STEP = 5

    fun clamp(percent: Int): Int = percent.coerceIn(MIN, MAX)
}

/**
 * Strength used to be one of three names. Data written then still loads: each name becomes the
 * percentage closest to what it played, and a number is read as is.
 */
object HapticStrengthSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("HapticStrength", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)

    override fun deserialize(decoder: Decoder): Int {
        val json = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val e = json.decodeJsonElement() as? JsonPrimitive ?: return HapticStrength.DEFAULT
        if (!e.isString) return e.content.toIntOrNull()?.let(HapticStrength::clamp) ?: HapticStrength.DEFAULT
        return when (e.content) {
            "Light" -> 20
            "Normal" -> 55
            "Strong" -> 100
            else -> HapticStrength.DEFAULT
        }
    }
}

/** The space kept under the favorites on Home, beyond the system bar: its range and default. */
object HomeSpacing {
    const val DEFAULT = 32
    const val MIN = 0

    /**
     * A sanity ceiling for stored values, not what the page offers: how far the gap can really go depends on
     * the screen and how many favorites there are, so the page works that out (FavoritesCapacity.maxGapDp).
     * Home itself hides rows that do not fit, so a stored value that has become too large (fewer rows fit, or
     * a bigger font) never pushes the clock off the screen.
     */
    const val LIMIT = 1000
    const val STEP = 4

    /** A stored or typed value brought into range and onto a step, so nothing odd can reach the layout. */
    fun clamp(dp: Int): Int = (dp.coerceIn(MIN, LIMIT) / STEP) * STEP
}

/**
 * One way to reach a contact the owner hid from search: the contact's result id and the action's recent id
 * (what tells it apart under that contact), plus the contact's name and the action's label as they read when it
 * was hidden, since a hidden entry is no longer in any result to read them from.
 */
@Serializable
data class HiddenContactAction(val contact: String, val action: String, val contactName: String, val label: String)

@Serializable
data class Settings(
    val sortByUsage: Boolean = true,
    /** Null means every calendar. */
    val enabledCalendars: Set<Long>? = null,
    /** The calendars search looks for events in, apart from the agenda's. Null means every calendar. */
    val searchCalendars: Set<Long>? = null,
    val disabledShortcuts: Set<String> = emptySet(),
    val dotColor: Long = 0xFFC9C9C9,
    /** A tick on each letter change while scrubbing the app list. Defaulted so older data decodes. */
    val haptics: Boolean = true,
    /**
     * Strength (0..100) of the tick while scrubbing the app list, and of the one tick a gesture gives when
     * it opens something (a drag that opens search or the notifications, a long press that opens a menu).
     * Defaulted so older data decodes.
     */
    @Serializable(with = HapticStrengthSerializer::class)
    val scrubHaptic: Int = HapticStrength.DEFAULT,
    @Serializable(with = HapticStrengthSerializer::class)
    val gestureHaptic: Int = HapticStrength.DEFAULT,
    /** Space below the last favorite, beyond the system bar, in dp. Defaulted so older data and backups decode. */
    val bottomGapDp: Int = HomeSpacing.DEFAULT,
    /** First-run setup was shown and finished or skipped; defaulted so older data decodes. */
    val setupSeen: Boolean = false,
    /** Search lists apps above everything else. Defaulted so older data and backups decode. */
    val appsFirst: Boolean = true,
    /** Search lists a contact above an app's shortcut. Defaulted so older data and backups decode. */
    val contactsFirst: Boolean = true,
    /** Search orders results by what was picked for the query before. Defaulted so older data and backups decode. */
    val learnFromPicks: Boolean = true,
    /** Ways to reach a contact that search leaves out. Defaulted so older data and backups decode. */
    val hiddenContactActions: List<HiddenContactAction> = emptyList(),
)

@Serializable
data class LauncherData(
    val version: Int = CURRENT_VERSION,
    val favorites: List<Entry> = emptyList(),
    val hiddenApps: Set<String> = emptySet(),
    /**
     * Every pop-up folder the owner has made, selected as a favorite or not. Unticking a folder
     * only removes it from [favorites]; it stays here with its contents. For a folder this entry
     * is the source of truth for its pop-up (resolved by id); the copy in [favorites] is a mirror
     * the editor keeps in sync. Added after version 1 shipped, so old data decodes with none.
     */
    val folders: List<Entry> = emptyList(),
    val userShortcuts: List<UserShortcut> = emptyList(),
    val settings: Settings = Settings(),
    val launchCounts: Map<String, Int> = emptyMap(),
    /**
     * The owner's name for an installed app, by component: one name per app, shown in Home, the app list,
     * search, pop-ups and the editor. Names given before this existed live on favorite entries and are read
     * as the app's name until the app is renamed again. Added later, so older data and backups decode empty.
     */
    val appNames: Map<String, String> = emptyMap(),
    /**
     * The pop-up of an app that is not a favorite, by component, so any app's sheet can be edited. A
     * favorite keeps its pop-up on its own entry; this is only read for an app with no favorite entry.
     * Added later, so older data and backups decode empty.
     */
    val appPopups: Map<String, Popup> = emptyMap(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
