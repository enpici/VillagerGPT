package tj.horner.villagergpt.conversation

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import kotlinx.coroutines.runBlocking
import com.destroystokyo.paper.entity.villager.ReputationType
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import tj.horner.villagergpt.VillagerGPT
import tj.horner.villagergpt.PersistentDataKeys
import tj.horner.villagergpt.events.VillagerConversationMessageEvent
import net.kyori.adventure.text.Component
import org.bukkit.persistence.PersistentDataType
import tj.horner.villagergpt.conversation.VillagerNameGenerator
import java.time.Duration
import java.util.*
import kotlin.random.Random

@OptIn(BetaOpenAI::class)
class VillagerConversation(private val plugin: VillagerGPT, val villager: Villager, val player: Player) {
    private var lastMessageAt: Date = Date()

    private lateinit var villagerName: String
    private var villagerSummary: String? = null
    private lateinit var personality: VillagerPersonality

    val messages = mutableListOf<ChatMessage>()
    var pendingResponse = false
    var ended = false

    init {
        startConversation()
    }

    fun addMessage(message: ChatMessage) {
        val event = VillagerConversationMessageEvent(this, message)
        plugin.server.pluginManager.callEvent(event)

        messages.add(message)
        lastMessageAt = Date()
    }

    fun removeLastMessage() {
        if (messages.size == 0) return
        messages.removeLast()
    }

    fun reset() {
        messages.clear()
        startConversation()
        lastMessageAt = Date()
    }

    fun hasExpired(): Boolean {
        val now = Date()
        val difference = now.time - lastMessageAt.time
        val duration = Duration.ofMillis(difference)
        return duration.toSeconds() > 120
    }

    fun hasPlayerLeft(): Boolean {
        if (player.location.world != villager.location.world) return true

        val radius = 20.0 // blocks?
        val radiusSquared = radius * radius
        val distanceSquared = player.location.distanceSquared(villager.location)
        return distanceSquared > radiusSquared
    }

    private fun startConversation() {
        val info = plugin.memory.getVillagerInfo(villager.uniqueId)
        if (info == null) {
            villagerName = VillagerNameGenerator.randomName()
            plugin.memory.insertVillager(villager.uniqueId, villagerName)
        } else {
            villagerName = info.name
            villagerSummary = info.summary
        }

        personality = getPersonality()

        villager.customName = net.kyori.adventure.text.Component.text(villagerName)
        villager.isCustomNameVisible = true

        var messageRole = ChatRole.System
        var prompt = generateSystemPrompt()

        val preambleMessageType = plugin.config.getString("preamble-message-type") ?: "system"
        if (preambleMessageType === "user") {
            messageRole = ChatRole.User
            prompt = "[SYSTEM MESSAGE]\n\n$prompt"
        }

        messages.add(
            ChatMessage(
                role = messageRole,
                content = prompt
            )
        )


        val historyLimit = plugin.config.getInt("memory.max-messages", 20)
        messages.addAll(plugin.memory.loadMessages(villager.uniqueId, historyLimit))


        val gossipLimit = plugin.config.getInt("gossip.max-entries", 30)
        val gossip = plugin.memory.loadGossip(villager.uniqueId, gossipLimit)
        if (gossip.isNotEmpty()) {
            val gossipText = gossip.joinToString("\n") { "- $it" }
            messages.add(ChatMessage(ChatRole.System, "Known gossip:\n$gossipText"))
        }
    }

    private fun generateSystemPrompt(): String {
        val world = villager.world
        val weather = if (world.hasStorm()) "Rainy" else "Sunny"
        val biome = world.getBiome(villager.location)
        val time = if (world.isDayTime) "Day" else "Night"
        val playerRep = getPlayerRepScore()

        plugin.logger.info("$villagerName is $personality")

        return """
        You are $villagerName, a villager in the game Minecraft where you can converse with the player and come up with new trades based on your conversation.

        ${villagerSummary?.let { "Previously: $it" } ?: "" }

        TRADING:

        To propose a new trade to the player, include it in your response with this format:

        TRADE[["{qty} {item}"],["{qty} {item}"]]ENDTRADE

        Where {item} is the Minecraft item ID (i.e., "minecraft:emerald") and {qty} is the amount of that item.
        You may choose to trade with emeralds or barter with players for other items; it is up to you.
        The first array is the items the YOU receive; the second is the item the PLAYER receives. The second array can only contain a single offer.
        {qty} is limited to 64.

        Examples:
        TRADE[["24 minecraft:emerald"],["1 minecraft:arrow"]]ENDTRADE
        TRADE[["12 minecraft:emerald","1 minecraft:book"],["1 minecraft:enchanted_book{StoredEnchantments:[{id:\"minecraft:unbreaking\",lvl:3}]}"]]ENDTRADE

        Trade rules:
        - Items must be designated by their Minecraft item ID, in the same format that the /give command accepts
        - Refuse trades that are unreasonable, such as requests for normally unobtainable blocks like bedrock
        - You do NOT need to supply a trade with every response, only when necessary
        - Don't give out items which are too powerful (i.e., heavily enchanted diamond swords). Make sure to price more powerful items appropriately as well
        - Take the player's reputation score into account when proposing trades
        - Trade items that are related to your profession
        - High-ball your initial offers; try to charge more than an item is worth
        - Be stingy with your consecutive offers. Try to haggle and find the best deal; make the player work for a good deal

        ACTIONS:
        
        You can also perform several actions as a villager:
        - SHAKE_HEAD: Shake your head at the player
        - SOUND_YES: Play a happy sound to the player
        - SOUND_NO: Play a sad/angry sound to the player
        - SOUND_AMBIENT: Play an ambient villager sound to the player
        
        To perform one of these actions, include "ACTION:{action name}" in your response.

        World information:
        - Time: $time
        - Weather: $weather
        - Biome: ${biome.name}

        Player information:
        - Name: ${player.name}
        - Reputation Score (range is -700 to 725, 0 is neutral, higher is better): $playerRep

        Personality:
        - Your Name: $villagerName
        - Your Profession: ${villager.profession.name}
        - ${personality.promptDescription()}
        - Act like a villager and stay in character the whole time
        - Do not reveal to the player the fact that you are a character in a video game, and do not mention Minecraft by name
        - Speak in a mildly medieval style
        """.trimIndent()
    }

    private fun getPersonality(): VillagerPersonality {
        val container = villager.persistentDataContainer
        val stored = container.get(PersistentDataKeys.PERSONALITY, PersistentDataType.STRING)
        return if (stored != null) {
            VillagerPersonality.valueOf(stored)
        } else {
            val personalities = VillagerPersonality.values()
            val rnd = Random(villager.uniqueId.mostSignificantBits)
            val chosen = personalities[rnd.nextInt(personalities.size)]
            container.set(PersistentDataKeys.PERSONALITY, PersistentDataType.STRING, chosen.name)
            chosen
        }
    }

    private fun getPlayerRepScore(): Int {
        var finalScore = 0
        val rep = villager.getReputation(player.uniqueId) ?: return 0

        ReputationType.values().forEach {
            val repTypeValue = rep.getReputation(it)
            finalScore += when (it) {
                ReputationType.MAJOR_POSITIVE -> repTypeValue * 5
                ReputationType.MINOR_POSITIVE -> repTypeValue
                ReputationType.MINOR_NEGATIVE -> -repTypeValue
                ReputationType.MAJOR_NEGATIVE -> -repTypeValue * 5
                ReputationType.TRADING -> repTypeValue
                else -> repTypeValue
            }
        }

        return finalScore
    }
}