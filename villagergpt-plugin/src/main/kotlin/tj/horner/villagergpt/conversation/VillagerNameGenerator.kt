package tj.horner.villagergpt.conversation

import kotlin.random.Random

object VillagerNameGenerator {
    private val names = listOf(
        "Alric", "Borin", "Cedric", "Dorian", "Edwin", "Fendrel", "Gareth",
        "Hadrian", "Ivor", "Jareth", "Kael", "Lorin", "Merek", "Nerin",
        "Orrin", "Percy", "Quentin", "Roderick", "Stefan", "Theron",
        "Ulric", "Valen", "Wendell", "Xander", "Yorick", "Zorin"
    )

    fun randomName(): String = names[Random.nextInt(names.size)]
}
