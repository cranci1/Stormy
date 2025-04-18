package club.maxstats.seraph.stats

import club.maxstats.kolour.util.Color
import java.util.*

fun getPlayerTag(uuid: UUID): PlayerTag {
    /* get tag from api */
    var tag = "NONE"

    /* Player is nicked */
    if (uuid.version() == 1)
        tag = "NICKED"

    /* Hardcoded for testing purposes */
    if (uuid == UUID.fromString("98a3cecc-3749-4981-b246-4fc8bb6d418c"))
        tag = "CREATOR"
    else if (uuid == UUID.fromString("e269bdd6-1ae9-4145-990c-fabefd02fdf0"))
        tag = "CREATOR"

    return PlayerTag.valueOf(tag)
}

enum class PlayerTag(
    val formattedName: String,
    val color: Color
) {
    NICKED("NICK", Color(255, 200, 200, 255)),
    NONE("", Color(170, 255, 238, 255)),
    CREATOR("Creator", Color(255, 87, 51, 255))
}