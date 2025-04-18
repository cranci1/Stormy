package club.maxstats.seraph.util

import club.maxstats.hyko.ApiKeyThrottleException
import club.maxstats.hyko.HypixelAPIException
import club.maxstats.hyko.Player
import club.maxstats.hyko.getPlayerFromUUID
import club.maxstats.seraph.Main
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import java.io.InputStream
import java.time.Duration
import java.util.*
import kotlin.math.abs

const val API_KEY = ""

private val playerCache = SimpleCache<UUID, Player>(500, Duration.ofMinutes(5))
private val safeJson = Json { ignoreUnknownKeys = true }
fun String.asResource(): InputStream = Main::class.java.classLoader.getResourceAsStream(this)
    ?: throw IllegalStateException("Failed to fetch resource $this")
fun now() = System.currentTimeMillis()
fun InputStream.readToString() = this.readBytes().toString(Charsets.UTF_8)
fun String.deserializeLocraw() {
    locrawInfo = safeJson.decodeFromString<LocrawInfo>(this)
}
infix fun Float.isCloseTo(other: Float) = abs(this - other) < 0.0001f
val mc = Minecraft.getMinecraft()
fun getScaledResolution(): ScaledResolution {
    return ScaledResolution(mc)
}
val isOnHypixel: Boolean
        get() =
            if (mc.theWorld != null && !mc.isSingleplayer)
                mc.currentServerData.serverIP.lowercase(Locale.getDefault()).contains("hypixel")
            else false
fun calculateScaleFactor(mc: Minecraft): Int {
    val displayWidth = mc.displayWidth
    val displayHeight = mc.displayHeight

    var scaleFactor = 1
    val isUnicode = mc.isUnicode

    val guiScale = mc.gameSettings.guiScale
    if (guiScale == 0) scaleFactor = 1000

    while (scaleFactor < guiScale && displayWidth / (scaleFactor + 1) >= 320 && displayHeight / (scaleFactor + 1) >= 240) ++scaleFactor
    if (isUnicode && scaleFactor % 2 != 0 && scaleFactor != 1) --scaleFactor

    return scaleFactor
}
private var keyThrottleStart: Long = 0L
private val statScope = CoroutineScope(Dispatchers.Default)
fun getOrPutPlayerData(uuid: UUID) : Player? {
    return playerCache.get(uuid)
        ?: try {
            if (now() - keyThrottleStart >= 70 * 1000) {
                statScope.launch {
                    getPlayerFromUUID(uuid.toString(), hypixelApiKey).also { playerCache.put(uuid, it) }
                }
            }
            return null
        } catch (ex: HypixelAPIException) {
            if (ex is ApiKeyThrottleException)
                keyThrottleStart = now()
            return null
        }
}
fun getStatRatio(num1: Number, num2: Number) : Double {
    if (num2 == 0) return num1.toDouble()
    return String.format(Locale.US, "%.2f", num1.toDouble() / num2.toDouble()).toDouble()
}
fun getPlayerData(uuid: UUID): Player? {
    return playerCache.get(uuid)
}

var hypixelApiKey : String = ""