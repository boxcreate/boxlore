package cx.aswin.boxlore.feature.home.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Paired cover-based marks for the related-show and episode rails. */
internal object RecommendationHeaderIcons {
    val YourShows = mark(
        "YourShows",
        "M3 4H5V19H3Q2 19 2 18V5Q2 4 3 4Z M7 2H9V19H7Z " +
            "M13 4H20Q22 4 22 6V19Q22 21 20 21H13Q11 21 11 19V6Q11 4 13 4Z " +
            "M13 6V19H20V6Z M15 9H18V12H15Z M14 15H19V16.5H14Z",
    )
    val SimilarShows = mark(
        "SimilarShows",
        "M3 3H12Q15 3 15 6V15Q15 18 12 18H3Q0 18 0 15V6Q0 3 3 3Z " +
            "M3 5V16H12Q13 16 13 15V6Q13 5 12 5Z " +
            "M17 5L22 6Q24 6.4 23.5 9L21 20Q20.5 22.5 18 22L8 20Q6 19.6 6 19L12 19Q16 19 16 15V6Z " +
            "M7.5 7Q10 7 10 9.5Q10 12 7.5 12Q5 12 5 9.5Q5 7 7.5 7Z M5 13H10V14.5H5Z",
    )
    val EpisodesToTry = mark(
        "EpisodesToTry",
        "M8 2L20 5Q23 5.8 22.2 9L19.5 20Q18.8 23 15.8 22.2L3.8 19.2Q1 18.5 1.8 15.5L4.5 4Q5.2 1.3 8 2Z " +
            "M10 7L8 15L16 12Z M4 21L14 23H5Q3 23 3 21Z",
    )

    private fun mark(name: String, data: String): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(data).toNodes(),
        fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        pathFillType = PathFillType.EvenOdd,
    ).build()
}
