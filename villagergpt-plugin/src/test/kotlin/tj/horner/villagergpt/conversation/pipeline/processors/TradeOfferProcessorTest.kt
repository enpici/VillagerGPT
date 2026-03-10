package io.github.enpici.villager.gpt.conversation.pipeline.processors

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.inventory.ItemFactory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.MerchantRecipe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.github.enpici.villager.gpt.conversation.VillagerConversation
import io.github.enpici.villager.gpt.conversation.pipeline.ConversationMessageAction

class TradeOfferProcessorTest {
    private val player = mockk<Player>(relaxed = true)
    private val villager = mockk<Villager>(relaxed = true)
    private val conversation = mockk<VillagerConversation>(relaxed = true)
    private val logger = mockk<java.util.logging.Logger>(relaxed = true)

    @BeforeEach
    fun setup() {
        every { villager.name() } returns Component.text("Aldeano")
        every { conversation.player } returns player
        every { conversation.villager } returns villager

        val itemFactory = mockk<ItemFactory>()
        every { itemFactory.createItemStack(any()) } answers {
            val fullId = firstArg<String>()
            val id = fullId.substringAfter(":", fullId)
            val material = Material.matchMaterial(id.uppercase())
                ?: throw IllegalArgumentException("Unknown material: $fullId")
            ItemStack(material)
        }

        val server = mockk<Server>()
        every { server.itemFactory } returns itemFactory

        mockkStatic(Bukkit::class)
        every { Bukkit.getServer() } returns server
    }

    @AfterEach
    fun teardown() {
        unmockkStatic(Bukkit::class)
    }

    @Test
    fun `valid trade creates villager recipe and clean player text`() {
        every { villager.resetOffers() } just runs
        every { villager.recipes = any() } just runs

        val processor = TradeOfferProcessor(logger)
        val actions = processor.processMessage(
            "Tengo oferta TRADE[[\"24 minecraft:emerald\"],[\"1 minecraft:arrow\"]]ENDTRADE listo",
            conversation
        )

        runActions(actions)

        val recipesSlot = slot<List<MerchantRecipe>>()
        verify { villager.recipes = capture(recipesSlot) }
        assertEquals(1, recipesSlot.captured.size)
        assertEquals(24, recipesSlot.captured[0].ingredients[0].amount)
        assertEquals(Material.EMERALD, recipesSlot.captured[0].ingredients[0].type)
        assertEquals(Material.ARROW, recipesSlot.captured[0].result.type)

        val sentComponent = slot<Component>()
        verify { player.sendMessage(capture(sentComponent)) }
        val plain = PlainTextComponentSerializer.plainText().serialize(sentComponent.captured)
        assertFalse(plain.contains("TRADE"))
    }

    @Test
    fun `invalid trade format is marked as invalid trade`() {
        val processor = TradeOfferProcessor(logger)

        val actions = processor.processMessage(
            "TRADE[[\"1 minecraft:emerald\"],[]]ENDTRADE",
            conversation
        )

        runActions(actions)

        val sentComponent = slot<Component>()
        verify { player.sendMessage(capture(sentComponent)) }
        val plain = PlainTextComponentSerializer.plainText().serialize(sentComponent.captured)
        assertTrue(plain.contains("[Invalid Trade]"))
    }

    @Test
    fun `invalid material and quantity are marked as invalid trades`() {
        val processor = TradeOfferProcessor(logger)

        val actions = processor.processMessage(
            """
            TRADE[["1 minecraft:not_a_real_item"],["1 minecraft:arrow"]]ENDTRADE
            TRADE[["999999999999 minecraft:emerald"],["1 minecraft:arrow"]]ENDTRADE
            """.trimIndent(),
            conversation
        )

        runActions(actions)

        val sentComponent = slot<Component>()
        verify { player.sendMessage(capture(sentComponent)) }
        val plain = PlainTextComponentSerializer.plainText().serialize(sentComponent.captured)
        val invalidCount = "\\[Invalid Trade]".toRegex().findAll(plain).count()
        assertEquals(2, invalidCount)
    }

    @Test
    fun `quantity is capped to 64 for valid material`() {
        every { villager.resetOffers() } just runs
        every { villager.recipes = any() } just runs

        val processor = TradeOfferProcessor(logger)
        val actions = processor.processMessage(
            "TRADE[[\"128 minecraft:emerald\"],[\"1 minecraft:arrow\"]]ENDTRADE",
            conversation
        )

        runActions(actions)

        val recipesSlot = slot<List<MerchantRecipe>>()
        verify { villager.recipes = capture(recipesSlot) }
        assertEquals(64, recipesSlot.captured[0].ingredients[0].amount)
    }

    @Test
    fun `final player text strips ACTION and TRADE raw markers after transform`() {
        val actionProcessor = ActionProcessor()
        val tradeProcessor = TradeOfferProcessor(logger)

        val original = "Saludos ACTION:SHAKE_HEAD TRADE[[\"2 minecraft:emerald\"],[\"1 minecraft:bread\"]]ENDTRADE"
        val transformed = actionProcessor.transformMessage(original, conversation)
        val actions = tradeProcessor.processMessage(transformed, conversation)

        runActions(actions)

        val sentComponent = slot<Component>()
        verify { player.sendMessage(capture(sentComponent)) }
        val plain = PlainTextComponentSerializer.plainText().serialize(sentComponent.captured)
        assertFalse(plain.contains("ACTION:"))
        assertFalse(plain.contains("TRADE"))
    }

    private fun runActions(actions: Collection<ConversationMessageAction>) {
        actions.forEach { it.run() }
    }
}
