package tj.horner.villagergpt.conversation.pipeline.processors

import io.mockk.every
import io.mockk.mockk
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tj.horner.villagergpt.conversation.VillagerConversation
import tj.horner.villagergpt.conversation.pipeline.actions.PlaySoundAction
import tj.horner.villagergpt.conversation.pipeline.actions.ShakeHeadAction

class ActionProcessorTest {
    private val player = mockk<Player>(relaxed = true)
    private val villager = mockk<Villager>(relaxed = true)
    private val conversation = mockk<VillagerConversation>(relaxed = true).also {
        every { it.player } returns player
        every { it.villager } returns villager
    }

    @Test
    fun `processMessage returns actions for recognized commands`() {
        val processor = ActionProcessor()

        val actions = processor.processMessage(
            "hola ACTION:SHAKE_HEAD ACTION:SOUND_YES ACTION:SOUND_AMBIENT",
            conversation
        )

        assertEquals(3, actions.size)
        assertTrue(actions.any { it is ShakeHeadAction })
        val soundActions = actions.filterIsInstance<PlaySoundAction>()
        assertEquals(2, soundActions.size)
    }

    @Test
    fun `processMessage ignores unrecognized commands`() {
        val processor = ActionProcessor()

        val actions = processor.processMessage(
            "ACTION:FLY ACTION:SOUND_LAUGH ACTION:DOES_NOT_EXIST",
            conversation
        )

        assertTrue(actions.isEmpty())
    }

    @Test
    fun `transformMessage strips raw ACTION tokens`() {
        val processor = ActionProcessor()

        val transformed = processor.transformMessage(
            "Hola ACTION:SHAKE_HEAD jugador ACTION:SOUND_NO",
            conversation
        )

        assertEquals("Hola  jugador", transformed)
        assertTrue(!transformed.contains("ACTION:"))
    }
}
