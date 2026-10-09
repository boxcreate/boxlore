package cx.aswin.boxlore.feature.info

import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EpisodeLinkVectorTest {
    @Test
    fun `Discord SVG arc flags retain the original curve in Android parsing`() {
        val moduleDir = File("src/main/res/drawable").takeIf(File::isDirectory)
            ?: File("feature/info/src/main/res/drawable")
        val xml = moduleDir.resolve("ic_link_discord.xml").readText()
        val path = Regex("android:pathData=\"([^\"]+)\"").find(xml)!!.groupValues[1]
        val nodes = PathParser().parsePathString(path).toNodes()
        val firstArc = nodes.filterIsInstance<PathNode.RelativeArcTo>().first()
        assertEquals(PathNode.RelativeArcTo(19.7913f, 19.7913f, 0f, false, false, -4.8851f, -1.5152f), firstArc)
    }
}
