package club.maxstats.seraph.command

import net.weavemc.loader.api.command.Command
import org.jetbrains.annotations.NotNull
import net.minecraft.client.Minecraft
import net.minecraft.util.ChatComponentText
import club.maxstats.seraph.util.hypixelApiKey

object ApiKeyManager {
    private var apiKey: String = ""
    fun setApiKey(key: String) {
        apiKey = key
    }
    fun getApiKey(): String = apiKey
}

class SeraphCommand : Command("seraph") {
    override fun handle(@NotNull args: Array<out String>) {
        if (args.isNotEmpty()) {
            val oldKey = hypixelApiKey
            hypixelApiKey = args[0]
            val msg = if (oldKey != hypixelApiKey)
                "§7[Seraph] §aAPI key set to: §r${args[0]}"
            else
                "§7[Seraph] §eAPI key unchanged."
            Minecraft.getMinecraft().thePlayer?.addChatMessage(ChatComponentText(msg))
        } else {
            Minecraft.getMinecraft().thePlayer?.addChatMessage(ChatComponentText("§cUsage: /seraph <APIKEY>"))
        }
    }
}