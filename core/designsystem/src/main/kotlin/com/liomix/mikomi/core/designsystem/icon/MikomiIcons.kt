package com.liomix.mikomi.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 由 material-icons 提取，勿手改路径。新增图标追加到本对象即可。 */
object MikomiIcons {

    /** ArrowBack（AutoMirrored.Rounded）。 */
    val Back: ImageVector by lazy {
        ImageVector.Builder(
            name = "Back",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = true,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 11f)
                horizontalLineTo(7.83f)
                lineToRelative(4.88f, -4.88f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0f, -1.42f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                lineToRelative(-6.59f, 6.59f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0f, 1.41f)
                lineToRelative(6.59f, 6.59f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
                lineTo(7.83f, 13f)
                horizontalLineTo(19f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                close()
            }
            .build()
    }

    /** ArrowBackIos（AutoMirrored.Rounded）。 */
    val BackIos: ImageVector by lazy {
        ImageVector.Builder(
            name = "BackIos",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = true,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(16.62f, 2.99f)
                curveToRelative(-0.49f, -0.49f, -1.28f, -0.49f, -1.77f, 0f)
                lineTo(6.54f, 11.3f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0f, 1.41f)
                lineToRelative(8.31f, 8.31f)
                curveToRelative(0.49f, 0.49f, 1.28f, 0.49f, 1.77f, 0f)
                reflectiveCurveToRelative(0.49f, -1.28f, 0f, -1.77f)
                lineTo(9.38f, 12f)
                lineToRelative(7.25f, -7.25f)
                curveToRelative(0.48f, -0.48f, 0.48f, -1.28f, -0.01f, -1.76f)
                close()
            }
            .build()
    }

    /** PlaylistPlay（AutoMirrored.Rounded）。 */
    val Playlist: ImageVector by lazy {
        ImageVector.Builder(
            name = "Playlist",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = true,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 10f)
                horizontalLineToRelative(10f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                lineTo(5f, 12f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                reflectiveCurveToRelative(0.45f, -1f, 1f, -1f)
                close()
                moveTo(5f, 6f)
                horizontalLineToRelative(10f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                lineTo(5f, 8f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                reflectiveCurveToRelative(0.45f, -1f, 1f, -1f)
                close()
                moveTo(5f, 14f)
                horizontalLineToRelative(6f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                lineTo(5f, 16f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                reflectiveCurveToRelative(0.45f, -1f, 1f, -1f)
                close()
                moveTo(14f, 14.88f)
                verticalLineToRelative(4.23f)
                curveToRelative(0f, 0.39f, 0.42f, 0.63f, 0.76f, 0.43f)
                lineToRelative(3.53f, -2.12f)
                curveToRelative(0.32f, -0.19f, 0.32f, -0.66f, 0f, -0.86f)
                lineToRelative(-3.53f, -2.12f)
                curveToRelative(-0.34f, -0.19f, -0.76f, 0.05f, -0.76f, 0.44f)
                close()
            }
            .build()
    }

    /** FormatAlignLeft（AutoMirrored.Rounded）。 */
    val TextAlign: ImageVector by lazy {
        ImageVector.Builder(
            name = "TextAlign",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = true,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(14f, 15f)
                lineTo(4f, 15f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(10f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                close()
                moveTo(14f, 7f)
                lineTo(4f, 7f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(10f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                close()
                moveTo(4f, 13f)
                horizontalLineToRelative(16f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                lineTo(4f, 11f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                close()
                moveTo(4f, 21f)
                horizontalLineToRelative(16f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                lineTo(4f, 19f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                close()
                moveTo(3f, 4f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(16f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                lineTo(4f, 3f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                close()
            }
            .build()
    }

    /** KeyboardArrowRight（AutoMirrored.Rounded）。 */
    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = true,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(9.29f, 15.88f)
                lineTo(13.17f, 12f)
                lineTo(9.29f, 8.12f)
                curveToRelative(-0.39f, -0.39f, -0.39f, -1.02f, 0f, -1.41f)
                curveToRelative(0.39f, -0.39f, 1.02f, -0.39f, 1.41f, 0f)
                lineToRelative(4.59f, 4.59f)
                curveToRelative(0.39f, 0.39f, 0.39f, 1.02f, 0f, 1.41f)
                lineTo(10.7f, 17.3f)
                curveToRelative(-0.39f, 0.39f, -1.02f, 0.39f, -1.41f, 0f)
                curveToRelative(-0.38f, -0.39f, -0.39f, -1.03f, 0f, -1.42f)
                close()
            }
            .build()
    }

    /** Sort（AutoMirrored.Rounded）。 */
    val Sort: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sort",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = true,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 18f)
                horizontalLineToRelative(4f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                lineTo(4f, 16f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                close()
                moveTo(3f, 7f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(16f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                lineTo(4f, 6f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                close()
                moveTo(4f, 13f)
                horizontalLineToRelative(10f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                lineTo(4f, 11f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                close()
            }
            .build()
    }

    /** Bookmark（Rounded）。 */
    val Bookmark: ImageVector by lazy {
        ImageVector.Builder(
            name = "Bookmark",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(17f, 3f)
                horizontalLineTo(7f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(16f)
                lineToRelative(7f, -3f)
                lineToRelative(7f, 3f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
            }
            .build()
    }

    /** Close（Rounded）。 */
    val Close: ImageVector by lazy {
        ImageVector.Builder(
            name = "Close",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(18.3f, 5.71f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                lineTo(12f, 10.59f)
                lineTo(7.11f, 5.7f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0f, 1.41f)
                lineTo(10.59f, 12f)
                lineTo(5.7f, 16.89f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0f, 1.41f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                lineTo(12f, 13.41f)
                lineToRelative(4.89f, 4.89f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
                lineTo(13.41f, 12f)
                lineToRelative(4.89f, -4.89f)
                curveToRelative(0.38f, -0.38f, 0.38f, -1.02f, 0f, -1.4f)
                close()
            }
            .build()
    }

    /** Edit（Rounded）。 */
    val Edit: ImageVector by lazy {
        ImageVector.Builder(
            name = "Edit",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 17.46f)
                verticalLineToRelative(3.04f)
                curveToRelative(0f, 0.28f, 0.22f, 0.5f, 0.5f, 0.5f)
                horizontalLineToRelative(3.04f)
                curveToRelative(0.13f, 0f, 0.26f, -0.05f, 0.35f, -0.15f)
                lineTo(17.81f, 9.94f)
                lineToRelative(-3.75f, -3.75f)
                lineTo(3.15f, 17.1f)
                curveToRelative(-0.1f, 0.1f, -0.15f, 0.22f, -0.15f, 0.36f)
                close()
                moveTo(20.71f, 7.04f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
                lineToRelative(-2.34f, -2.34f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                lineToRelative(-1.83f, 1.83f)
                lineToRelative(3.75f, 3.75f)
                lineToRelative(1.83f, -1.83f)
                close()
            }
            .build()
    }

    /** Forum（Rounded）。 */
    val Forum: ImageVector by lazy {
        ImageVector.Builder(
            name = "Forum",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 6f)
                horizontalLineToRelative(-1f)
                verticalLineToRelative(8f)
                curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
                lineTo(6f, 15f)
                verticalLineToRelative(1f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(10f)
                lineToRelative(4f, 4f)
                lineTo(22f, 8f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(17f, 11f)
                lineTo(17f, 4f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                lineTo(4f, 2f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(13f)
                lineToRelative(4f, -4f)
                horizontalLineToRelative(9f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                close()
            }
            .build()
    }

    /** Home（Rounded）。 */
    val Home: ImageVector by lazy {
        ImageVector.Builder(
            name = "Home",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(10f, 19f)
                verticalLineToRelative(-5f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(5f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(3f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                verticalLineToRelative(-7f)
                horizontalLineToRelative(1.7f)
                curveToRelative(0.46f, 0f, 0.68f, -0.57f, 0.33f, -0.87f)
                lineTo(12.67f, 3.6f)
                curveToRelative(-0.38f, -0.34f, -0.96f, -0.34f, -1.34f, 0f)
                lineToRelative(-8.36f, 7.53f)
                curveToRelative(-0.34f, 0.3f, -0.13f, 0.87f, 0.33f, 0.87f)
                horizontalLineTo(5f)
                verticalLineToRelative(7f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(3f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                close()
            }
            .build()
    }

    /** Map（Rounded）。 */
    val Map: ImageVector by lazy {
        ImageVector.Builder(
            name = "Map",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(14.65f, 4.98f)
                lineToRelative(-5f, -1.75f)
                curveToRelative(-0.42f, -0.15f, -0.88f, -0.15f, -1.3f, -0.01f)
                lineTo(4.36f, 4.56f)
                curveTo(3.55f, 4.84f, 3f, 5.6f, 3f, 6.46f)
                verticalLineToRelative(11.85f)
                curveToRelative(0f, 1.41f, 1.41f, 2.37f, 2.72f, 1.86f)
                lineToRelative(2.93f, -1.14f)
                curveToRelative(0.22f, -0.09f, 0.47f, -0.09f, 0.69f, -0.01f)
                lineToRelative(5f, 1.75f)
                curveToRelative(0.42f, 0.15f, 0.88f, 0.15f, 1.3f, 0.01f)
                lineToRelative(3.99f, -1.34f)
                curveToRelative(0.81f, -0.27f, 1.36f, -1.04f, 1.36f, -1.9f)
                verticalLineTo(5.69f)
                curveToRelative(0f, -1.41f, -1.41f, -2.37f, -2.72f, -1.86f)
                lineToRelative(-2.93f, 1.14f)
                curveToRelative(-0.22f, 0.08f, -0.46f, 0.09f, -0.69f, 0.01f)
                close()
                moveTo(15f, 18.89f)
                lineToRelative(-6f, -2.11f)
                verticalLineTo(5.11f)
                lineToRelative(6f, 2.11f)
                verticalLineToRelative(11.67f)
                close()
            }
            .build()
    }

    /** Person（Rounded）。 */
    val Person: ImageVector by lazy {
        ImageVector.Builder(
            name = "Person",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 12f)
                curveToRelative(2.21f, 0f, 4f, -1.79f, 4f, -4f)
                reflectiveCurveToRelative(-1.79f, -4f, -4f, -4f)
                reflectiveCurveToRelative(-4f, 1.79f, -4f, 4f)
                reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
                close()
                moveTo(12f, 14f)
                curveToRelative(-2.67f, 0f, -8f, 1.34f, -8f, 4f)
                verticalLineToRelative(1f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(14f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                verticalLineToRelative(-1f)
                curveToRelative(0f, -2.66f, -5.33f, -4f, -8f, -4f)
                close()
            }
            .build()
    }

    /** Place（Rounded）。 */
    val Place: ImageVector by lazy {
        ImageVector.Builder(
            name = "Place",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                curveToRelative(-4.2f, 0f, -8f, 3.22f, -8f, 8.2f)
                curveToRelative(0f, 3.18f, 2.45f, 6.92f, 7.34f, 11.23f)
                curveToRelative(0.38f, 0.33f, 0.95f, 0.33f, 1.33f, 0f)
                curveTo(17.55f, 17.12f, 20f, 13.38f, 20f, 10.2f)
                curveTo(20f, 5.22f, 16.2f, 2f, 12f, 2f)
                close()
                moveTo(12f, 12f)
                curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
                curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
                curveToRelative(1.1f, 0f, 2f, 0.9f, 2f, 2f)
                curveTo(14f, 11.1f, 13.1f, 12f, 12f, 12f)
                close()
            }
            .build()
    }

    /** PlayArrow（Rounded）。 */
    val Play: ImageVector by lazy {
        ImageVector.Builder(
            name = "Play",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 6.82f)
                verticalLineToRelative(10.36f)
                curveToRelative(0f, 0.79f, 0.87f, 1.27f, 1.54f, 0.84f)
                lineToRelative(8.14f, -5.18f)
                curveToRelative(0.62f, -0.39f, 0.62f, -1.29f, 0f, -1.69f)
                lineTo(9.54f, 5.98f)
                curveTo(8.87f, 5.55f, 8f, 6.03f, 8f, 6.82f)
                close()
            }
            .build()
    }

    /** Search（Rounded）。 */
    val Search: ImageVector by lazy {
        ImageVector.Builder(
            name = "Search",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(15.5f, 14f)
                horizontalLineToRelative(-0.79f)
                lineToRelative(-0.28f, -0.27f)
                curveToRelative(1.2f, -1.4f, 1.82f, -3.31f, 1.48f, -5.34f)
                curveToRelative(-0.47f, -2.78f, -2.79f, -5f, -5.59f, -5.34f)
                curveToRelative(-4.23f, -0.52f, -7.79f, 3.04f, -7.27f, 7.27f)
                curveToRelative(0.34f, 2.8f, 2.56f, 5.12f, 5.34f, 5.59f)
                curveToRelative(2.03f, 0.34f, 3.94f, -0.28f, 5.34f, -1.48f)
                lineToRelative(0.27f, 0.28f)
                verticalLineToRelative(0.79f)
                lineToRelative(4.25f, 4.25f)
                curveToRelative(0.41f, 0.41f, 1.08f, 0.41f, 1.49f, 0f)
                curveToRelative(0.41f, -0.41f, 0.41f, -1.08f, 0f, -1.49f)
                lineTo(15.5f, 14f)
                close()
                moveTo(9.5f, 14f)
                curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
                reflectiveCurveTo(7.01f, 5f, 9.5f, 5f)
                reflectiveCurveTo(14f, 7.01f, 14f, 9.5f)
                reflectiveCurveTo(11.99f, 14f, 9.5f, 14f)
                close()
            }
            .build()
    }

    /** Settings（Rounded）。 */
    val Settings: ImageVector by lazy {
        ImageVector.Builder(
            name = "Settings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(19.5f, 12f)
                curveToRelative(0f, -0.23f, -0.01f, -0.45f, -0.03f, -0.68f)
                lineToRelative(1.86f, -1.41f)
                curveToRelative(0.4f, -0.3f, 0.51f, -0.86f, 0.26f, -1.3f)
                lineToRelative(-1.87f, -3.23f)
                curveToRelative(-0.25f, -0.44f, -0.79f, -0.62f, -1.25f, -0.42f)
                lineToRelative(-2.15f, 0.91f)
                curveToRelative(-0.37f, -0.26f, -0.76f, -0.49f, -1.17f, -0.68f)
                lineToRelative(-0.29f, -2.31f)
                curveTo(14.8f, 2.38f, 14.37f, 2f, 13.87f, 2f)
                horizontalLineToRelative(-3.73f)
                curveTo(9.63f, 2f, 9.2f, 2.38f, 9.14f, 2.88f)
                lineTo(8.85f, 5.19f)
                curveToRelative(-0.41f, 0.19f, -0.8f, 0.42f, -1.17f, 0.68f)
                lineTo(5.53f, 4.96f)
                curveToRelative(-0.46f, -0.2f, -1f, -0.02f, -1.25f, 0.42f)
                lineTo(2.41f, 8.62f)
                curveToRelative(-0.25f, 0.44f, -0.14f, 0.99f, 0.26f, 1.3f)
                lineToRelative(1.86f, 1.41f)
                curveTo(4.51f, 11.55f, 4.5f, 11.77f, 4.5f, 12f)
                reflectiveCurveToRelative(0.01f, 0.45f, 0.03f, 0.68f)
                lineToRelative(-1.86f, 1.41f)
                curveToRelative(-0.4f, 0.3f, -0.51f, 0.86f, -0.26f, 1.3f)
                lineToRelative(1.87f, 3.23f)
                curveToRelative(0.25f, 0.44f, 0.79f, 0.62f, 1.25f, 0.42f)
                lineToRelative(2.15f, -0.91f)
                curveToRelative(0.37f, 0.26f, 0.76f, 0.49f, 1.17f, 0.68f)
                lineToRelative(0.29f, 2.31f)
                curveTo(9.2f, 21.62f, 9.63f, 22f, 10.13f, 22f)
                horizontalLineToRelative(3.73f)
                curveToRelative(0.5f, 0f, 0.93f, -0.38f, 0.99f, -0.88f)
                lineToRelative(0.29f, -2.31f)
                curveToRelative(0.41f, -0.19f, 0.8f, -0.42f, 1.17f, -0.68f)
                lineToRelative(2.15f, 0.91f)
                curveToRelative(0.46f, 0.2f, 1f, 0.02f, 1.25f, -0.42f)
                lineToRelative(1.87f, -3.23f)
                curveToRelative(0.25f, -0.44f, 0.14f, -0.99f, -0.26f, -1.3f)
                lineToRelative(-1.86f, -1.41f)
                curveTo(19.49f, 12.45f, 19.5f, 12.23f, 19.5f, 12f)
                close()
                moveTo(12.04f, 15.5f)
                curveToRelative(-1.93f, 0f, -3.5f, -1.57f, -3.5f, -3.5f)
                reflectiveCurveToRelative(1.57f, -3.5f, 3.5f, -3.5f)
                reflectiveCurveToRelative(3.5f, 1.57f, 3.5f, 3.5f)
                reflectiveCurveTo(13.97f, 15.5f, 12.04f, 15.5f)
                close()
            }
            .build()
    }

    /** Star（Rounded）。 */
    val Star: ImageVector by lazy {
        ImageVector.Builder(
            name = "Star",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 17.27f)
                lineToRelative(4.15f, 2.51f)
                curveToRelative(0.76f, 0.46f, 1.69f, -0.22f, 1.49f, -1.08f)
                lineToRelative(-1.1f, -4.72f)
                lineToRelative(3.67f, -3.18f)
                curveToRelative(0.67f, -0.58f, 0.31f, -1.68f, -0.57f, -1.75f)
                lineToRelative(-4.83f, -0.41f)
                lineToRelative(-1.89f, -4.46f)
                curveToRelative(-0.34f, -0.81f, -1.5f, -0.81f, -1.84f, 0f)
                lineTo(9.19f, 8.63f)
                lineTo(4.36f, 9.04f)
                curveToRelative(-0.88f, 0.07f, -1.24f, 1.17f, -0.57f, 1.75f)
                lineToRelative(3.67f, 3.18f)
                lineToRelative(-1.1f, 4.72f)
                curveToRelative(-0.2f, 0.86f, 0.73f, 1.54f, 1.49f, 1.08f)
                lineTo(12f, 17.27f)
                close()
            }
            .build()
    }

    /** Add（Rounded）。 */
    val Add: ImageVector by lazy {
        ImageVector.Builder(
            name = "Add",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(18f, 13f)
                horizontalLineToRelative(-5f)
                verticalLineToRelative(5f)
                curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
                reflectiveCurveToRelative(-1f, -0.45f, -1f, -1f)
                verticalLineToRelative(-5f)
                horizontalLineTo(6f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                reflectiveCurveToRelative(0.45f, -1f, 1f, -1f)
                horizontalLineToRelative(5f)
                verticalLineTo(6f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                reflectiveCurveToRelative(1f, 0.45f, 1f, 1f)
                verticalLineToRelative(5f)
                horizontalLineToRelative(5f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                close()
            }
            .build()
    }

    /** CalendarMonth（Rounded）。 */
    val Calendar: ImageVector by lazy {
        ImageVector.Builder(
            name = "Calendar",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(17f, 2f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                verticalLineToRelative(1f)
                horizontalLineTo(8f)
                verticalLineTo(3f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                reflectiveCurveTo(6f, 2.45f, 6f, 3f)
                verticalLineToRelative(1f)
                horizontalLineTo(5f)
                curveTo(3.89f, 4f, 3.01f, 4.9f, 3.01f, 6f)
                lineTo(3f, 20f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(6f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                horizontalLineToRelative(-1f)
                verticalLineTo(3f)
                curveTo(18f, 2.45f, 17.55f, 2f, 17f, 2f)
                close()
                moveTo(19f, 20f)
                horizontalLineTo(5f)
                verticalLineTo(10f)
                horizontalLineToRelative(14f)
                verticalLineTo(20f)
                close()
                moveTo(11f, 13f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                reflectiveCurveToRelative(1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                reflectiveCurveTo(11f, 13.55f, 11f, 13f)
                close()
                moveTo(7f, 13f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                reflectiveCurveToRelative(1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                reflectiveCurveTo(7f, 13.55f, 7f, 13f)
                close()
                moveTo(15f, 13f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                reflectiveCurveToRelative(1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                reflectiveCurveTo(15f, 13.55f, 15f, 13f)
                close()
                moveTo(11f, 17f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                reflectiveCurveToRelative(1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                reflectiveCurveTo(11f, 17.55f, 11f, 17f)
                close()
                moveTo(7f, 17f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                reflectiveCurveToRelative(1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                reflectiveCurveTo(7f, 17.55f, 7f, 17f)
                close()
                moveTo(15f, 17f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                reflectiveCurveToRelative(1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                reflectiveCurveTo(15f, 17.55f, 15f, 17f)
                close()
            }
            .build()
    }

    /** Category（Rounded）。 */
    val Category: ImageVector by lazy {
        ImageVector.Builder(
            name = "Category",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(11.15f, 3.4f)
                lineTo(7.43f, 9.48f)
                curveToRelative(-0.41f, 0.66f, 0.07f, 1.52f, 0.85f, 1.52f)
                horizontalLineToRelative(7.43f)
                curveToRelative(0.78f, 0f, 1.26f, -0.86f, 0.85f, -1.52f)
                lineTo(12.85f, 3.4f)
                curveToRelative(-0.39f, -0.64f, -1.31f, -0.64f, -1.7f, 0f)
                close()
            }
            .path(fill = SolidColor(Color.Black)) {
                moveTo(17.5f, 17.5f)
                moveToRelative(-4.5f, 0f)
                arcToRelative(4.5f, 4.5f, 0f, true, true, 9f, 0f)
                arcToRelative(4.5f, 4.5f, 0f, true, true, -9f, 0f)
            }
            .path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 21.5f)
                horizontalLineToRelative(6f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                verticalLineToRelative(-6f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                horizontalLineTo(4f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                verticalLineToRelative(6f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                close()
            }
            .build()
    }

    /** ChatBubbleOutline（Rounded）。 */
    val Comment: ImageVector by lazy {
        ImageVector.Builder(
            name = "Comment",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 4f)
                verticalLineToRelative(12f)
                horizontalLineTo(5.17f)
                lineTo(4f, 17.17f)
                verticalLineTo(4f)
                horizontalLineTo(20f)
                moveTo(20f, 2f)
                horizontalLineTo(4f)
                curveTo(2.9f, 2f, 2f, 2.9f, 2f, 4f)
                verticalLineToRelative(15.59f)
                curveToRelative(0f, 0.89f, 1.08f, 1.34f, 1.71f, 0.71f)
                lineTo(6f, 18f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(4f)
                curveTo(22f, 2.9f, 21.1f, 2f, 20f, 2f)
                lineTo(20f, 2f)
                close()
            }
            .build()
    }

    /** CloudOff（Rounded）。 */
    val CloudOff: ImageVector by lazy {
        ImageVector.Builder(
            name = "CloudOff",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(24f, 15f)
                curveToRelative(0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
                curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
                curveToRelative(-1.33f, 0f, -2.57f, 0.36f, -3.65f, 0.97f)
                lineToRelative(1.49f, 1.49f)
                curveTo(10.51f, 6.17f, 11.23f, 6f, 12f, 6f)
                curveToRelative(3.04f, 0f, 5.5f, 2.46f, 5.5f, 5.5f)
                verticalLineToRelative(0.5f)
                horizontalLineTo(19f)
                curveToRelative(1.66f, 0f, 3f, 1.34f, 3f, 3f)
                curveToRelative(0f, 0.99f, -0.48f, 1.85f, -1.21f, 2.4f)
                lineToRelative(1.41f, 1.41f)
                curveToRelative(1.09f, -0.92f, 1.8f, -2.27f, 1.8f, -3.81f)
                close()
                moveTo(3.71f, 4.56f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0f, 1.41f)
                lineToRelative(2.06f, 2.06f)
                horizontalLineToRelative(-0.42f)
                curveToRelative(-3.28f, 0.35f, -5.76f, 3.34f, -5.29f, 6.79f)
                curveTo(0.46f, 17.84f, 3.19f, 20f, 6.22f, 20f)
                horizontalLineToRelative(11.51f)
                lineToRelative(1.29f, 1.29f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
                lineTo(5.12f, 4.56f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                close()
                moveTo(6f, 18f)
                curveToRelative(-2.21f, 0f, -4f, -1.79f, -4f, -4f)
                reflectiveCurveToRelative(1.79f, -4f, 4f, -4f)
                horizontalLineToRelative(1.73f)
                lineToRelative(8f, 8f)
                horizontalLineTo(6f)
                close()
            }
            .build()
    }

    /** DeleteOutline（Rounded）。 */
    val Delete: ImageVector by lazy {
        ImageVector.Builder(
            name = "Delete",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 19f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(8f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                lineTo(18f, 9f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                lineTo(8f, 7f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(10f)
                close()
                moveTo(9f, 9f)
                horizontalLineToRelative(6f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                verticalLineToRelative(8f)
                curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
                lineTo(9f, 19f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                verticalLineToRelative(-8f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                close()
                moveTo(15.5f, 4f)
                lineToRelative(-0.71f, -0.71f)
                curveToRelative(-0.18f, -0.18f, -0.44f, -0.29f, -0.7f, -0.29f)
                lineTo(9.91f, 3f)
                curveToRelative(-0.26f, 0f, -0.52f, 0.11f, -0.7f, 0.29f)
                lineTo(8.5f, 4f)
                lineTo(6f, 4f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(12f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                horizontalLineToRelative(-2.5f)
                close()
            }
            .build()
    }

    /** Email（Rounded）。 */
    val Email: ImageVector by lazy {
        ImageVector.Builder(
            name = "Email",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 4f)
                lineTo(4f, 4f)
                curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(2f, 18f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(16f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                lineTo(22f, 6f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(19.6f, 8.25f)
                lineToRelative(-7.07f, 4.42f)
                curveToRelative(-0.32f, 0.2f, -0.74f, 0.2f, -1.06f, 0f)
                lineTo(4.4f, 8.25f)
                curveToRelative(-0.25f, -0.16f, -0.4f, -0.43f, -0.4f, -0.72f)
                curveToRelative(0f, -0.67f, 0.73f, -1.07f, 1.3f, -0.72f)
                lineTo(12f, 11f)
                lineToRelative(6.7f, -4.19f)
                curveToRelative(0.57f, -0.35f, 1.3f, 0.05f, 1.3f, 0.72f)
                curveToRelative(0f, 0.29f, -0.15f, 0.56f, -0.4f, 0.72f)
                close()
            }
            .build()
    }

    /** EmojiEvents（Rounded）。 */
    val Trophy: ImageVector by lazy {
        ImageVector.Builder(
            name = "Trophy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 5f)
                horizontalLineToRelative(-2f)
                verticalLineTo(4f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                horizontalLineTo(8f)
                curveTo(7.45f, 3f, 7f, 3.45f, 7f, 4f)
                verticalLineToRelative(1f)
                horizontalLineTo(5f)
                curveTo(3.9f, 5f, 3f, 5.9f, 3f, 7f)
                verticalLineToRelative(1f)
                curveToRelative(0f, 2.55f, 1.92f, 4.63f, 4.39f, 4.94f)
                curveToRelative(0.63f, 1.5f, 1.98f, 2.63f, 3.61f, 2.96f)
                verticalLineTo(19f)
                horizontalLineTo(8f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                verticalLineToRelative(0f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(8f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                verticalLineToRelative(0f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                horizontalLineToRelative(-3f)
                verticalLineToRelative(-3.1f)
                curveToRelative(1.63f, -0.33f, 2.98f, -1.46f, 3.61f, -2.96f)
                curveTo(19.08f, 12.63f, 21f, 10.55f, 21f, 8f)
                verticalLineTo(7f)
                curveTo(21f, 5.9f, 20.1f, 5f, 19f, 5f)
                close()
                moveTo(5f, 8f)
                verticalLineTo(7f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(3.82f)
                curveTo(5.84f, 10.4f, 5f, 9.3f, 5f, 8f)
                close()
                moveTo(19f, 8f)
                curveToRelative(0f, 1.3f, -0.84f, 2.4f, -2f, 2.82f)
                verticalLineTo(7f)
                horizontalLineToRelative(2f)
                verticalLineTo(8f)
                close()
            }
            .build()
    }

    /** ExpandLess（Rounded）。 */
    val ExpandLess: ImageVector by lazy {
        ImageVector.Builder(
            name = "ExpandLess",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(11.29f, 8.71f)
                lineTo(6.7f, 13.3f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0f, 1.41f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                lineTo(12f, 10.83f)
                lineToRelative(3.88f, 3.88f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
                lineTo(12.7f, 8.71f)
                curveToRelative(-0.38f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                close()
            }
            .build()
    }

    /** ExpandMore（Rounded）。 */
    val ExpandMore: ImageVector by lazy {
        ImageVector.Builder(
            name = "ExpandMore",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(15.88f, 9.29f)
                lineTo(12f, 13.17f)
                lineTo(8.12f, 9.29f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0f, 1.41f)
                lineToRelative(4.59f, 4.59f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                lineToRelative(4.59f, -4.59f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
                curveToRelative(-0.39f, -0.38f, -1.03f, -0.39f, -1.42f, 0f)
                close()
            }
            .build()
    }

    /** Extension（Rounded）。 */
    val Extension: ImageVector by lazy {
        ImageVector.Builder(
            name = "Extension",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(20.5f, 11f)
                horizontalLineTo(19f)
                verticalLineTo(7f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                horizontalLineToRelative(-4f)
                verticalLineTo(3.5f)
                curveTo(13f, 2.12f, 11.88f, 1f, 10.5f, 1f)
                reflectiveCurveTo(8f, 2.12f, 8f, 3.5f)
                verticalLineTo(5f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                verticalLineToRelative(3.8f)
                horizontalLineTo(3.5f)
                curveToRelative(1.49f, 0f, 2.7f, 1.21f, 2.7f, 2.7f)
                reflectiveCurveToRelative(-1.21f, 2.7f, -2.7f, 2.7f)
                horizontalLineTo(2f)
                verticalLineTo(20f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(3.8f)
                verticalLineToRelative(-1.5f)
                curveToRelative(0f, -1.49f, 1.21f, -2.7f, 2.7f, -2.7f)
                reflectiveCurveToRelative(2.7f, 1.21f, 2.7f, 2.7f)
                verticalLineTo(22f)
                horizontalLineTo(17f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineToRelative(-4f)
                horizontalLineToRelative(1.5f)
                curveToRelative(1.38f, 0f, 2.5f, -1.12f, 2.5f, -2.5f)
                reflectiveCurveTo(21.88f, 11f, 20.5f, 11f)
                close()
            }
            .build()
    }

    /** History（Rounded）。 */
    val History: ImageVector by lazy {
        ImageVector.Builder(
            name = "History",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(13.26f, 3f)
                curveTo(8.17f, 2.86f, 4f, 6.95f, 4f, 12f)
                lineTo(2.21f, 12f)
                curveToRelative(-0.45f, 0f, -0.67f, 0.54f, -0.35f, 0.85f)
                lineToRelative(2.79f, 2.8f)
                curveToRelative(0.2f, 0.2f, 0.51f, 0.2f, 0.71f, 0f)
                lineToRelative(2.79f, -2.8f)
                curveToRelative(0.31f, -0.31f, 0.09f, -0.85f, -0.36f, -0.85f)
                lineTo(6f, 12f)
                curveToRelative(0f, -3.9f, 3.18f, -7.05f, 7.1f, -7f)
                curveToRelative(3.72f, 0.05f, 6.85f, 3.18f, 6.9f, 6.9f)
                curveToRelative(0.05f, 3.91f, -3.1f, 7.1f, -7f, 7.1f)
                curveToRelative(-1.61f, 0f, -3.1f, -0.55f, -4.28f, -1.48f)
                curveToRelative(-0.4f, -0.31f, -0.96f, -0.28f, -1.32f, 0.08f)
                curveToRelative(-0.42f, 0.42f, -0.39f, 1.13f, 0.08f, 1.49f)
                curveTo(9f, 20.29f, 10.91f, 21f, 13f, 21f)
                curveToRelative(5.05f, 0f, 9.14f, -4.17f, 9f, -9.26f)
                curveToRelative(-0.13f, -4.69f, -4.05f, -8.61f, -8.74f, -8.74f)
                close()
                moveTo(12.75f, 8f)
                curveToRelative(-0.41f, 0f, -0.75f, 0.34f, -0.75f, 0.75f)
                verticalLineToRelative(3.68f)
                curveToRelative(0f, 0.35f, 0.19f, 0.68f, 0.49f, 0.86f)
                lineToRelative(3.12f, 1.85f)
                curveToRelative(0.36f, 0.21f, 0.82f, 0.09f, 1.03f, -0.26f)
                curveToRelative(0.21f, -0.36f, 0.09f, -0.82f, -0.26f, -1.03f)
                lineToRelative(-2.88f, -1.71f)
                verticalLineToRelative(-3.4f)
                curveToRelative(0f, -0.4f, -0.34f, -0.74f, -0.75f, -0.74f)
                close()
            }
            .build()
    }

    /** Language（Rounded）。 */
    val Language: ImageVector by lazy {
        ImageVector.Builder(
            name = "Language",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(11.99f, 2f)
                curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.47f, 10f, 9.99f, 10f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                reflectiveCurveTo(17.52f, 2f, 11.99f, 2f)
                close()
                moveTo(18.92f, 8f)
                horizontalLineToRelative(-2.95f)
                curveToRelative(-0.32f, -1.25f, -0.78f, -2.45f, -1.38f, -3.56f)
                curveToRelative(1.84f, 0.63f, 3.37f, 1.91f, 4.33f, 3.56f)
                close()
                moveTo(12f, 4.04f)
                curveToRelative(0.83f, 1.2f, 1.48f, 2.53f, 1.91f, 3.96f)
                horizontalLineToRelative(-3.82f)
                curveToRelative(0.43f, -1.43f, 1.08f, -2.76f, 1.91f, -3.96f)
                close()
                moveTo(4.26f, 14f)
                curveTo(4.1f, 13.36f, 4f, 12.69f, 4f, 12f)
                reflectiveCurveToRelative(0.1f, -1.36f, 0.26f, -2f)
                horizontalLineToRelative(3.38f)
                curveToRelative(-0.08f, 0.66f, -0.14f, 1.32f, -0.14f, 2f)
                reflectiveCurveToRelative(0.06f, 1.34f, 0.14f, 2f)
                lineTo(4.26f, 14f)
                close()
                moveTo(5.08f, 16f)
                horizontalLineToRelative(2.95f)
                curveToRelative(0.32f, 1.25f, 0.78f, 2.45f, 1.38f, 3.56f)
                curveToRelative(-1.84f, -0.63f, -3.37f, -1.9f, -4.33f, -3.56f)
                close()
                moveTo(8.03f, 8f)
                lineTo(5.08f, 8f)
                curveToRelative(0.96f, -1.66f, 2.49f, -2.93f, 4.33f, -3.56f)
                curveTo(8.81f, 5.55f, 8.35f, 6.75f, 8.03f, 8f)
                close()
                moveTo(12f, 19.96f)
                curveToRelative(-0.83f, -1.2f, -1.48f, -2.53f, -1.91f, -3.96f)
                horizontalLineToRelative(3.82f)
                curveToRelative(-0.43f, 1.43f, -1.08f, 2.76f, -1.91f, 3.96f)
                close()
                moveTo(14.34f, 14f)
                lineTo(9.66f, 14f)
                curveToRelative(-0.09f, -0.66f, -0.16f, -1.32f, -0.16f, -2f)
                reflectiveCurveToRelative(0.07f, -1.35f, 0.16f, -2f)
                horizontalLineToRelative(4.68f)
                curveToRelative(0.09f, 0.65f, 0.16f, 1.32f, 0.16f, 2f)
                reflectiveCurveToRelative(-0.07f, 1.34f, -0.16f, 2f)
                close()
                moveTo(14.59f, 19.56f)
                curveToRelative(0.6f, -1.11f, 1.06f, -2.31f, 1.38f, -3.56f)
                horizontalLineToRelative(2.95f)
                curveToRelative(-0.96f, 1.65f, -2.49f, 2.93f, -4.33f, 3.56f)
                close()
                moveTo(16.36f, 14f)
                curveToRelative(0.08f, -0.66f, 0.14f, -1.32f, 0.14f, -2f)
                reflectiveCurveToRelative(-0.06f, -1.34f, -0.14f, -2f)
                horizontalLineToRelative(3.38f)
                curveToRelative(0.16f, 0.64f, 0.26f, 1.31f, 0.26f, 2f)
                reflectiveCurveToRelative(-0.1f, 1.36f, -0.26f, 2f)
                horizontalLineToRelative(-3.38f)
                close()
            }
            .build()
    }

    /** NorthWest（Rounded）。 */
    val NorthWest: ImageVector by lazy {
        ImageVector.Builder(
            name = "NorthWest",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 15f)
                lineTo(6f, 15f)
                curveToRelative(0.56f, 0f, 1f, -0.45f, 1f, -1f)
                verticalLineTo(8.41f)
                lineTo(17.89f, 19.3f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                lineToRelative(0f, 0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
                lineTo(8.41f, 7f)
                horizontalLineTo(14f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                verticalLineTo(6f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                horizontalLineTo(6f)
                curveTo(5.45f, 5f, 5f, 5.45f, 5f, 6f)
                verticalLineTo(14f)
                curveTo(5f, 14.55f, 5.45f, 15f, 6f, 15f)
                close()
            }
            .build()
    }

    /** Palette（Rounded）。 */
    val Palette: ImageVector by lazy {
        ImageVector.Builder(
            name = "Palette",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                curveTo(6.49f, 2f, 2f, 6.49f, 2f, 12f)
                reflectiveCurveToRelative(4.49f, 10f, 10f, 10f)
                curveToRelative(1.38f, 0f, 2.5f, -1.12f, 2.5f, -2.5f)
                curveToRelative(0f, -0.61f, -0.23f, -1.2f, -0.64f, -1.67f)
                curveToRelative(-0.08f, -0.1f, -0.13f, -0.21f, -0.13f, -0.33f)
                curveToRelative(0f, -0.28f, 0.22f, -0.5f, 0.5f, -0.5f)
                horizontalLineTo(16f)
                curveToRelative(3.31f, 0f, 6f, -2.69f, 6f, -6f)
                curveTo(22f, 6.04f, 17.51f, 2f, 12f, 2f)
                close()
                moveTo(17.5f, 13f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                curveToRelative(0f, -0.83f, 0.67f, -1.5f, 1.5f, -1.5f)
                reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
                curveTo(19f, 12.33f, 18.33f, 13f, 17.5f, 13f)
                close()
                moveTo(14.5f, 9f)
                curveTo(13.67f, 9f, 13f, 8.33f, 13f, 7.5f)
                curveTo(13f, 6.67f, 13.67f, 6f, 14.5f, 6f)
                reflectiveCurveTo(16f, 6.67f, 16f, 7.5f)
                curveTo(16f, 8.33f, 15.33f, 9f, 14.5f, 9f)
                close()
                moveTo(5f, 11.5f)
                curveTo(5f, 10.67f, 5.67f, 10f, 6.5f, 10f)
                reflectiveCurveTo(8f, 10.67f, 8f, 11.5f)
                curveTo(8f, 12.33f, 7.33f, 13f, 6.5f, 13f)
                reflectiveCurveTo(5f, 12.33f, 5f, 11.5f)
                close()
                moveTo(11f, 7.5f)
                curveTo(11f, 8.33f, 10.33f, 9f, 9.5f, 9f)
                reflectiveCurveTo(8f, 8.33f, 8f, 7.5f)
                curveTo(8f, 6.67f, 8.67f, 6f, 9.5f, 6f)
                reflectiveCurveTo(11f, 6.67f, 11f, 7.5f)
                close()
            }
            .build()
    }

    /** PlayCircleOutline（Rounded）。 */
    val PlayCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "PlayCircle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(10.8f, 15.9f)
                lineToRelative(4.67f, -3.5f)
                curveToRelative(0.27f, -0.2f, 0.27f, -0.6f, 0f, -0.8f)
                lineTo(10.8f, 8.1f)
                curveToRelative(-0.33f, -0.25f, -0.8f, -0.01f, -0.8f, 0.4f)
                verticalLineToRelative(7f)
                curveToRelative(0f, 0.41f, 0.47f, 0.65f, 0.8f, 0.4f)
                close()
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(12f, 20f)
                curveToRelative(-4.41f, 0f, -8f, -3.59f, -8f, -8f)
                reflectiveCurveToRelative(3.59f, -8f, 8f, -8f)
                reflectiveCurveToRelative(8f, 3.59f, 8f, 8f)
                reflectiveCurveToRelative(-3.59f, 8f, -8f, 8f)
                close()
            }
            .build()
    }

    /** Refresh（Rounded）。 */
    val Refresh: ImageVector by lazy {
        ImageVector.Builder(
            name = "Refresh",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(17.65f, 6.35f)
                curveToRelative(-1.63f, -1.63f, -3.94f, -2.57f, -6.48f, -2.31f)
                curveToRelative(-3.67f, 0.37f, -6.69f, 3.35f, -7.1f, 7.02f)
                curveTo(3.52f, 15.91f, 7.27f, 20f, 12f, 20f)
                curveToRelative(3.19f, 0f, 5.93f, -1.87f, 7.21f, -4.56f)
                curveToRelative(0.32f, -0.67f, -0.16f, -1.44f, -0.9f, -1.44f)
                curveToRelative(-0.37f, 0f, -0.72f, 0.2f, -0.88f, 0.53f)
                curveToRelative(-1.13f, 2.43f, -3.84f, 3.97f, -6.8f, 3.31f)
                curveToRelative(-2.22f, -0.49f, -4.01f, -2.3f, -4.48f, -4.52f)
                curveTo(5.31f, 9.44f, 8.26f, 6f, 12f, 6f)
                curveToRelative(1.66f, 0f, 3.14f, 0.69f, 4.22f, 1.78f)
                lineToRelative(-1.51f, 1.51f)
                curveToRelative(-0.63f, 0.63f, -0.19f, 1.71f, 0.7f, 1.71f)
                horizontalLineTo(19f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                verticalLineTo(6.41f)
                curveToRelative(0f, -0.89f, -1.08f, -1.34f, -1.71f, -0.71f)
                lineToRelative(-0.64f, 0.65f)
                close()
            }
            .build()
    }

    /** Remove（Rounded）。 */
    val Remove: ImageVector by lazy {
        ImageVector.Builder(
            name = "Remove",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(18f, 13f)
                horizontalLineTo(6f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                reflectiveCurveToRelative(0.45f, -1f, 1f, -1f)
                horizontalLineToRelative(12f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                close()
            }
            .build()
    }

    /** Whatshot（Rounded）。 */
    val Whatshot: ImageVector by lazy {
        ImageVector.Builder(
            name = "Whatshot",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(17.09f, 4.56f)
                curveToRelative(-0.7f, -1.03f, -1.5f, -1.99f, -2.4f, -2.85f)
                curveToRelative(-0.35f, -0.34f, -0.94f, -0.02f, -0.84f, 0.46f)
                curveToRelative(0.19f, 0.94f, 0.39f, 2.18f, 0.39f, 3.29f)
                curveToRelative(0f, 2.06f, -1.35f, 3.73f, -3.41f, 3.73f)
                curveToRelative(-1.54f, 0f, -2.8f, -0.93f, -3.35f, -2.26f)
                curveToRelative(-0.1f, -0.2f, -0.14f, -0.32f, -0.2f, -0.54f)
                curveToRelative(-0.11f, -0.42f, -0.66f, -0.55f, -0.9f, -0.18f)
                curveToRelative(-0.18f, 0.27f, -0.35f, 0.54f, -0.51f, 0.83f)
                curveTo(4.68f, 9.08f, 4f, 11.46f, 4f, 14f)
                curveToRelative(0f, 4.42f, 3.58f, 8f, 8f, 8f)
                reflectiveCurveToRelative(8f, -3.58f, 8f, -8f)
                curveToRelative(0f, -3.49f, -1.08f, -6.73f, -2.91f, -9.44f)
                close()
                moveTo(11.71f, 19f)
                curveToRelative(-1.78f, 0f, -3.22f, -1.4f, -3.22f, -3.14f)
                curveToRelative(0f, -1.62f, 1.05f, -2.76f, 2.81f, -3.12f)
                curveToRelative(1.47f, -0.3f, 2.98f, -0.93f, 4.03f, -1.92f)
                curveToRelative(0.28f, -0.26f, 0.74f, -0.14f, 0.82f, 0.23f)
                curveToRelative(0.23f, 1.02f, 0.35f, 2.08f, 0.35f, 3.15f)
                curveToRelative(0.01f, 2.65f, -2.14f, 4.8f, -4.79f, 4.8f)
                close()
            }
            .build()
    }

    /** ArrowUpward（Rounded）。 */
    val ArrowUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(13f, 19f)
                verticalLineTo(7.83f)
                lineToRelative(4.88f, 4.88f)
                curveToRelative(0.39f, 0.39f, 1.03f, 0.39f, 1.42f, 0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
                lineToRelative(-6.59f, -6.59f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                lineToRelative(-6.6f, 6.58f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0f, 1.41f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                lineTo(11f, 7.83f)
                verticalLineTo(19f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                reflectiveCurveToRelative(1f, -0.45f, 1f, -1f)
                close()
            }
            .build()
    }

    /** BrightnessLow（Rounded）。 */
    val BrightnessLow: ImageVector by lazy {
        ImageVector.Builder(
            name = "BrightnessLow",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 15.31f)
                lineToRelative(1.9f, -1.9f)
                curveToRelative(0.78f, -0.78f, 0.78f, -2.05f, 0f, -2.83f)
                lineTo(20f, 8.69f)
                verticalLineTo(6f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                horizontalLineToRelative(-2.69f)
                lineToRelative(-1.9f, -1.9f)
                curveToRelative(-0.78f, -0.78f, -2.05f, -0.78f, -2.83f, 0f)
                lineTo(8.69f, 4f)
                horizontalLineTo(6f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(2.69f)
                lineToRelative(-1.9f, 1.9f)
                curveToRelative(-0.78f, 0.78f, -0.78f, 2.05f, 0f, 2.83f)
                lineToRelative(1.9f, 1.9f)
                verticalLineTo(18f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(2.69f)
                lineToRelative(1.9f, 1.9f)
                curveToRelative(0.78f, 0.78f, 2.05f, 0.78f, 2.83f, 0f)
                lineToRelative(1.9f, -1.9f)
                horizontalLineTo(18f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineToRelative(-2.69f)
                close()
                moveTo(12f, 18f)
                curveToRelative(-3.31f, 0f, -6f, -2.69f, -6f, -6f)
                reflectiveCurveToRelative(2.69f, -6f, 6f, -6f)
                reflectiveCurveToRelative(6f, 2.69f, 6f, 6f)
                reflectiveCurveToRelative(-2.69f, 6f, -6f, 6f)
                close()
            }
            .build()
    }

    /** BrightnessMedium（Rounded）。 */
    val BrightnessMedium: ImageVector by lazy {
        ImageVector.Builder(
            name = "BrightnessMedium",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 15.31f)
                lineToRelative(1.9f, -1.9f)
                curveToRelative(0.78f, -0.78f, 0.78f, -2.05f, 0f, -2.83f)
                lineTo(20f, 8.69f)
                lineTo(20f, 6f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                horizontalLineToRelative(-2.69f)
                lineToRelative(-1.9f, -1.9f)
                curveToRelative(-0.78f, -0.78f, -2.05f, -0.78f, -2.83f, 0f)
                lineTo(8.69f, 4f)
                lineTo(6f, 4f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(2.69f)
                lineToRelative(-1.9f, 1.9f)
                curveToRelative(-0.78f, 0.78f, -0.78f, 2.05f, 0f, 2.83f)
                lineToRelative(1.9f, 1.9f)
                lineTo(4f, 18f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(2.69f)
                lineToRelative(1.9f, 1.9f)
                curveToRelative(0.78f, 0.78f, 2.05f, 0.78f, 2.83f, 0f)
                lineToRelative(1.9f, -1.9f)
                lineTo(18f, 20f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineToRelative(-2.69f)
                close()
                moveTo(12f, 16.9f)
                lineTo(12f, 7.1f)
                curveToRelative(0f, -0.61f, 0.55f, -1.11f, 1.15f, -0.99f)
                curveTo(15.91f, 6.65f, 18f, 9.08f, 18f, 12f)
                reflectiveCurveToRelative(-2.09f, 5.35f, -4.85f, 5.89f)
                curveToRelative(-0.6f, 0.12f, -1.15f, -0.38f, -1.15f, -0.99f)
                close()
            }
            .build()
    }

    /** Check（Rounded）。 */
    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "Check",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(9f, 16.17f)
                lineTo(5.53f, 12.7f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0f, 1.41f)
                lineToRelative(4.18f, 4.18f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                lineTo(20.29f, 7.71f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                lineTo(9f, 16.17f)
                close()
            }
            .build()
    }

    /** CheckCircle（Rounded）。 */
    val CheckCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "CheckCircle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(9.29f, 16.29f)
                lineTo(5.7f, 12.7f)
                curveToRelative(-0.39f, -0.39f, -0.39f, -1.02f, 0f, -1.41f)
                curveToRelative(0.39f, -0.39f, 1.02f, -0.39f, 1.41f, 0f)
                lineTo(10f, 14.17f)
                lineToRelative(6.88f, -6.88f)
                curveToRelative(0.39f, -0.39f, 1.02f, -0.39f, 1.41f, 0f)
                curveToRelative(0.39f, 0.39f, 0.39f, 1.02f, 0f, 1.41f)
                lineToRelative(-7.59f, 7.59f)
                curveToRelative(-0.38f, 0.39f, -1.02f, 0.39f, -1.41f, 0f)
                close()
            }
            .build()
    }

    /** ContentPaste（Rounded）。 */
    val ContentPaste: ImageVector by lazy {
        ImageVector.Builder(
            name = "ContentPaste",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 2f)
                horizontalLineToRelative(-4.18f)
                curveTo(14.4f, 0.84f, 13.3f, 0f, 12f, 0f)
                reflectiveCurveTo(9.6f, 0.84f, 9.18f, 2f)
                lineTo(5f, 2f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(16f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                lineTo(21f, 4f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(12f, 2f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                reflectiveCurveToRelative(-1f, -0.45f, -1f, -1f)
                reflectiveCurveToRelative(0.45f, -1f, 1f, -1f)
                close()
                moveTo(18f, 20f)
                lineTo(6f, 20f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                lineTo(5f, 5f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                horizontalLineToRelative(1f)
                verticalLineToRelative(1f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(6f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                lineTo(17f, 4f)
                horizontalLineToRelative(1f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
                close()
            }
            .build()
    }

    /** EditNote（Rounded）。 */
    val EditNote: ImageVector by lazy {
        ImageVector.Builder(
            name = "EditNote",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(14f, 11f)
                curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
                horizontalLineTo(4f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                reflectiveCurveToRelative(0.45f, -1f, 1f, -1f)
                horizontalLineToRelative(9f)
                curveTo(13.55f, 10f, 14f, 10.45f, 14f, 11f)
                close()
                moveTo(3f, 7f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(9f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                horizontalLineTo(4f)
                curveTo(3.45f, 6f, 3f, 6.45f, 3f, 7f)
                close()
                moveTo(10f, 15f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                horizontalLineTo(4f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(5f)
                curveTo(9.55f, 16f, 10f, 15.55f, 10f, 15f)
                close()
                moveTo(18.01f, 12.87f)
                lineToRelative(0.71f, -0.71f)
                curveToRelative(0.39f, -0.39f, 1.02f, -0.39f, 1.41f, 0f)
                lineToRelative(0.71f, 0.71f)
                curveToRelative(0.39f, 0.39f, 0.39f, 1.02f, 0f, 1.41f)
                lineToRelative(-0.71f, 0.71f)
                lineTo(18.01f, 12.87f)
                close()
                moveTo(17.3f, 13.58f)
                lineToRelative(-5.16f, 5.16f)
                curveTo(12.05f, 18.83f, 12f, 18.95f, 12f, 19.09f)
                verticalLineToRelative(1.41f)
                curveToRelative(0f, 0.28f, 0.22f, 0.5f, 0.5f, 0.5f)
                horizontalLineToRelative(1.41f)
                curveToRelative(0.13f, 0f, 0.26f, -0.05f, 0.35f, -0.15f)
                lineToRelative(5.16f, -5.16f)
                lineTo(17.3f, 13.58f)
                close()
            }
            .build()
    }

    /** ErrorOutline（Rounded）。 */
    val Error: ImageVector by lazy {
        ImageVector.Builder(
            name = "Error",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 7f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                verticalLineToRelative(4f)
                curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
                reflectiveCurveToRelative(-1f, -0.45f, -1f, -1f)
                lineTo(11f, 8f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                close()
                moveTo(11.99f, 2f)
                curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.47f, 10f, 9.99f, 10f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                reflectiveCurveTo(17.52f, 2f, 11.99f, 2f)
                close()
                moveTo(12f, 20f)
                curveToRelative(-4.42f, 0f, -8f, -3.58f, -8f, -8f)
                reflectiveCurveToRelative(3.58f, -8f, 8f, -8f)
                reflectiveCurveToRelative(8f, 3.58f, 8f, 8f)
                reflectiveCurveToRelative(-3.58f, 8f, -8f, 8f)
                close()
                moveTo(13f, 17f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
            }
            .build()
    }

    /** FileOpen（Rounded）。 */
    val FileOpen: ImageVector by lazy {
        ImageVector.Builder(
            name = "FileOpen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(13.17f, 2f)
                horizontalLineTo(6f)
                curveTo(4.9f, 2f, 4f, 2.9f, 4f, 4f)
                verticalLineToRelative(16f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(9f)
                verticalLineToRelative(-6f)
                curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
                horizontalLineToRelative(3f)
                verticalLineTo(8.83f)
                curveToRelative(0f, -0.53f, -0.21f, -1.04f, -0.59f, -1.41f)
                lineToRelative(-4.83f, -4.83f)
                curveTo(14.21f, 2.21f, 13.7f, 2f, 13.17f, 2f)
                close()
                moveTo(13f, 8f)
                verticalLineTo(3.5f)
                lineTo(18.5f, 9f)
                horizontalLineTo(14f)
                curveTo(13.45f, 9f, 13f, 8.55f, 13f, 8f)
                close()
                moveTo(22.66f, 17f)
                curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
                horizontalLineToRelative(-1.24f)
                lineToRelative(2.24f, 2.24f)
                curveToRelative(0.39f, 0.39f, 0.39f, 1.02f, 0f, 1.41f)
                lineToRelative(0f, 0f)
                curveToRelative(-0.39f, 0.39f, -1.02f, 0.39f, -1.41f, 0f)
                lineTo(19f, 19.41f)
                lineToRelative(0f, 1.24f)
                curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                verticalLineTo(17f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                horizontalLineToRelative(3.66f)
                curveTo(22.21f, 16f, 22.66f, 16.45f, 22.66f, 17f)
                close()
            }
            .build()
    }

    /** FitScreen（Rounded）。 */
    val FitScreen: ImageVector by lazy {
        ImageVector.Builder(
            name = "FitScreen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(18f, 4f)
                horizontalLineToRelative(2f)
                curveToRelative(1.1f, 0f, 2f, 0.9f, 2f, 2f)
                verticalLineToRelative(2f)
                curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
                horizontalLineToRelative(0f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                verticalLineTo(6f)
                horizontalLineToRelative(-2f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                verticalLineToRelative(0f)
                curveTo(17f, 4.45f, 17.45f, 4f, 18f, 4f)
                close()
                moveTo(4f, 8f)
                lineToRelative(0f, -2f)
                horizontalLineToRelative(2f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                verticalLineToRelative(0f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                horizontalLineTo(4f)
                curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
                lineToRelative(0f, 2f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(0f)
                curveTo(3.55f, 9f, 4f, 8.55f, 4f, 8f)
                close()
                moveTo(20f, 16f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(-2f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                verticalLineToRelative(0f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(2f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineToRelative(-2f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                horizontalLineToRelative(0f)
                curveTo(20.45f, 15f, 20f, 15.45f, 20f, 16f)
                close()
                moveTo(6f, 18f)
                horizontalLineTo(4f)
                verticalLineToRelative(-2f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                horizontalLineToRelative(0f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                verticalLineToRelative(2f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(2f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                verticalLineToRelative(0f)
                curveTo(7f, 18.45f, 6.55f, 18f, 6f, 18f)
                close()
                moveTo(16f, 8f)
                horizontalLineTo(8f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(4f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(8f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineToRelative(-4f)
                curveTo(18f, 8.9f, 17.1f, 8f, 16f, 8f)
                close()
            }
            .build()
    }

    /** Fullscreen（Rounded）。 */
    val Fullscreen: ImageVector by lazy {
        ImageVector.Builder(
            name = "Fullscreen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 14f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                verticalLineToRelative(3f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(3f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                lineTo(7f, 17f)
                verticalLineToRelative(-2f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                close()
                moveTo(6f, 10f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                lineTo(7f, 7f)
                horizontalLineToRelative(2f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                lineTo(6f, 5f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                verticalLineToRelative(3f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                close()
                moveTo(17f, 17f)
                horizontalLineToRelative(-2f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(3f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                verticalLineToRelative(-3f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f)
                verticalLineToRelative(2f)
                close()
                moveTo(14f, 6f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                reflectiveCurveToRelative(1f, -0.45f, 1f, -1f)
                lineTo(19f, 6f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                horizontalLineToRelative(-3f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                close()
            }
            .build()
    }

    /** FullscreenExit（Rounded）。 */
    val FullscreenExit: ImageVector by lazy {
        ImageVector.Builder(
            name = "FullscreenExit",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 16f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                reflectiveCurveToRelative(1f, -0.45f, 1f, -1f)
                verticalLineToRelative(-3f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                lineTo(6f, 14f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                close()
                moveTo(8f, 8f)
                lineTo(6f, 8f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(3f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                lineTo(10f, 6f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f)
                verticalLineToRelative(2f)
                close()
                moveTo(15f, 19f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                horizontalLineToRelative(-3f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                verticalLineToRelative(3f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                close()
                moveTo(16f, 8f)
                lineTo(16f, 6f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f)
                verticalLineToRelative(3f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(3f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                horizontalLineToRelative(-2f)
                close()
            }
            .build()
    }

    /** Link（Rounded）。 */
    val Link: ImageVector by lazy {
        ImageVector.Builder(
            name = "Link",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(17f, 7f)
                horizontalLineToRelative(-3f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(3f)
                curveToRelative(1.65f, 0f, 3f, 1.35f, 3f, 3f)
                reflectiveCurveToRelative(-1.35f, 3f, -3f, 3f)
                horizontalLineToRelative(-3f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(3f)
                curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
                reflectiveCurveTo(19.76f, 7f, 17f, 7f)
                close()
                moveTo(8f, 12f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(6f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                horizontalLineTo(9f)
                curveTo(8.45f, 11f, 8f, 11.45f, 8f, 12f)
                close()
                moveTo(10f, 15f)
                horizontalLineTo(7f)
                curveToRelative(-1.65f, 0f, -3f, -1.35f, -3f, -3f)
                reflectiveCurveToRelative(1.35f, -3f, 3f, -3f)
                horizontalLineToRelative(3f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                horizontalLineTo(7f)
                curveToRelative(-2.76f, 0f, -5f, 2.24f, -5f, 5f)
                reflectiveCurveToRelative(2.24f, 5f, 5f, 5f)
                horizontalLineToRelative(3f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                curveTo(11f, 15.45f, 10.55f, 15f, 10f, 15f)
                close()
            }
            .build()
    }

    /** Lock（Rounded）。 */
    val Lock: ImageVector by lazy {
        ImageVector.Builder(
            name = "Lock",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(18f, 8f)
                horizontalLineToRelative(-1f)
                lineTo(17f, 6f)
                curveToRelative(0f, -2.76f, -2.24f, -5f, -5f, -5f)
                reflectiveCurveTo(7f, 3.24f, 7f, 6f)
                verticalLineToRelative(2f)
                lineTo(6f, 8f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(10f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(12f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                lineTo(20f, 10f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(12f, 17f)
                curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
                reflectiveCurveToRelative(0.9f, -2f, 2f, -2f)
                reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
                reflectiveCurveToRelative(-0.9f, 2f, -2f, 2f)
                close()
                moveTo(9f, 8f)
                lineTo(9f, 6f)
                curveToRelative(0f, -1.66f, 1.34f, -3f, 3f, -3f)
                reflectiveCurveToRelative(3f, 1.34f, 3f, 3f)
                verticalLineToRelative(2f)
                lineTo(9f, 8f)
                close()
            }
            .build()
    }

    /** LockOpen（Rounded）。 */
    val LockOpen: ImageVector by lazy {
        ImageVector.Builder(
            name = "LockOpen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 13f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
                reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                close()
                moveTo(18f, 8f)
                horizontalLineToRelative(-1f)
                lineTo(17f, 6f)
                curveToRelative(0f, -2.76f, -2.24f, -5f, -5f, -5f)
                curveToRelative(-2.28f, 0f, -4.27f, 1.54f, -4.84f, 3.75f)
                curveToRelative(-0.14f, 0.54f, 0.18f, 1.08f, 0.72f, 1.22f)
                curveToRelative(0.53f, 0.14f, 1.08f, -0.18f, 1.22f, -0.72f)
                curveTo(9.44f, 3.93f, 10.63f, 3f, 12f, 3f)
                curveToRelative(1.65f, 0f, 3f, 1.35f, 3f, 3f)
                verticalLineToRelative(2f)
                lineTo(6f, 8f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(10f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(12f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                lineTo(20f, 10f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(18f, 19f)
                curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
                lineTo(7f, 20f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                verticalLineToRelative(-8f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                horizontalLineToRelative(10f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                verticalLineToRelative(8f)
                close()
            }
            .build()
    }

    /** MoreVert（Rounded）。 */
    val MoreVert: ImageVector by lazy {
        ImageVector.Builder(
            name = "MoreVert",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 8f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
                reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                close()
                moveTo(12f, 10f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
                reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                close()
                moveTo(12f, 16f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
                reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                close()
            }
            .build()
    }

    /** Pause（Rounded）。 */
    val Pause: ImageVector by lazy {
        ImageVector.Builder(
            name = "Pause",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 19f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                lineTo(10f, 7f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
                verticalLineToRelative(10f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                close()
                moveTo(14f, 7f)
                verticalLineToRelative(10f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
                lineTo(18f, 7f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
                close()
            }
            .build()
    }

    /** PhotoCamera（Rounded）。 */
    val PhotoCamera: ImageVector by lazy {
        ImageVector.Builder(
            name = "PhotoCamera",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 12f)
                moveToRelative(-3f, 0f)
                arcToRelative(3f, 3f, 0f, true, true, 6f, 0f)
                arcToRelative(3f, 3f, 0f, true, true, -6f, 0f)
            }
            .path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 4f)
                horizontalLineToRelative(-3.17f)
                lineToRelative(-1.24f, -1.35f)
                curveToRelative(-0.37f, -0.41f, -0.91f, -0.65f, -1.47f, -0.65f)
                lineTo(9.88f, 2f)
                curveToRelative(-0.56f, 0f, -1.1f, 0.24f, -1.48f, 0.65f)
                lineTo(7.17f, 4f)
                lineTo(4f, 4f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(12f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(16f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                lineTo(22f, 6f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(12f, 17f)
                curveToRelative(-2.76f, 0f, -5f, -2.24f, -5f, -5f)
                reflectiveCurveToRelative(2.24f, -5f, 5f, -5f)
                reflectiveCurveToRelative(5f, 2.24f, 5f, 5f)
                reflectiveCurveToRelative(-2.24f, 5f, -5f, 5f)
                close()
            }
            .build()
    }

    /** RadioButtonUnchecked（Rounded）。 */
    val RadioUnchecked: ImageVector by lazy {
        ImageVector.Builder(
            name = "RadioUnchecked",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(12f, 20f)
                curveToRelative(-4.42f, 0f, -8f, -3.58f, -8f, -8f)
                reflectiveCurveToRelative(3.58f, -8f, 8f, -8f)
                reflectiveCurveToRelative(8f, 3.58f, 8f, 8f)
                reflectiveCurveToRelative(-3.58f, 8f, -8f, 8f)
                close()
            }
            .build()
    }

    /** Science（Rounded）。 */
    val Science: ImageVector by lazy {
        ImageVector.Builder(
            name = "Science",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(20.54f, 17.73f)
                lineTo(15f, 11f)
                verticalLineTo(5f)
                horizontalLineToRelative(1f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                horizontalLineTo(8f)
                curveTo(7.45f, 3f, 7f, 3.45f, 7f, 4f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(1f)
                verticalLineToRelative(6f)
                lineToRelative(-5.54f, 6.73f)
                curveTo(3.14f, 18.12f, 3f, 18.56f, 3f, 19f)
                curveToRelative(0.01f, 1.03f, 0.82f, 2f, 2f, 2f)
                horizontalLineTo(19f)
                curveToRelative(1.19f, 0f, 2f, -0.97f, 2f, -2f)
                curveTo(21f, 18.56f, 20.86f, 18.12f, 20.54f, 17.73f)
                close()
            }
            .build()
    }

    /** SearchOff（Rounded）。 */
    val SearchOff: ImageVector by lazy {
        ImageVector.Builder(
            name = "SearchOff",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(15.5f, 14f)
                horizontalLineToRelative(-0.79f)
                lineToRelative(-0.28f, -0.27f)
                curveToRelative(1.2f, -1.4f, 1.82f, -3.31f, 1.48f, -5.34f)
                curveToRelative(-0.47f, -2.78f, -2.79f, -4.99f, -5.58f, -5.34f)
                curveTo(6.54f, 2.58f, 3.3f, 5.38f, 3.03f, 9f)
                horizontalLineToRelative(2.02f)
                curveToRelative(0.24f, -2.12f, 1.92f, -3.8f, 4.06f, -3.98f)
                curveTo(11.65f, 4.8f, 14f, 6.95f, 14f, 9.5f)
                curveToRelative(0f, 2.49f, -2.01f, 4.5f, -4.5f, 4.5f)
                curveToRelative(-0.17f, 0f, -0.33f, -0.03f, -0.5f, -0.05f)
                lineToRelative(0f, 2.02f)
                curveToRelative(0f, 0f, 0f, 0f, 0.01f, 0.01f)
                curveToRelative(1.8f, 0.13f, 3.47f, -0.47f, 4.72f, -1.55f)
                lineTo(14f, 14.71f)
                verticalLineToRelative(0.79f)
                lineToRelative(4.25f, 4.25f)
                curveToRelative(0.41f, 0.41f, 1.08f, 0.41f, 1.49f, 0f)
                lineToRelative(0f, 0f)
                curveToRelative(0.41f, -0.41f, 0.41f, -1.08f, 0f, -1.49f)
                lineTo(15.5f, 14f)
                close()
            }
            .path(fill = SolidColor(Color.Black)) {
                moveTo(6.12f, 11.17f)
                lineTo(4f, 13.29f)
                lineToRelative(-2.12f, -2.12f)
                curveToRelative(-0.2f, -0.2f, -0.51f, -0.2f, -0.71f, 0f)
                lineToRelative(0f, 0f)
                curveToRelative(-0.2f, 0.2f, -0.2f, 0.51f, 0f, 0.71f)
                lineTo(3.29f, 14f)
                lineToRelative(-2.12f, 2.12f)
                curveToRelative(-0.2f, 0.2f, -0.2f, 0.51f, 0f, 0.71f)
                lineToRelative(0f, 0f)
                curveToRelative(0.2f, 0.2f, 0.51f, 0.2f, 0.71f, 0f)
                lineTo(4f, 14.71f)
                lineToRelative(2.12f, 2.12f)
                curveToRelative(0.2f, 0.2f, 0.51f, 0.2f, 0.71f, 0f)
                lineToRelative(0f, 0f)
                curveToRelative(0.2f, -0.2f, 0.2f, -0.51f, 0f, -0.71f)
                lineTo(4.71f, 14f)
                lineToRelative(2.12f, -2.12f)
                curveToRelative(0.2f, -0.2f, 0.2f, -0.51f, 0f, -0.71f)
                lineToRelative(0f, 0f)
                curveTo(6.63f, 10.98f, 6.32f, 10.98f, 6.12f, 11.17f)
                close()
            }
            .build()
    }

    /** Share（Rounded）。 */
    val Share: ImageVector by lazy {
        ImageVector.Builder(
            name = "Share",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(18f, 16.08f)
                curveToRelative(-0.76f, 0f, -1.44f, 0.3f, -1.96f, 0.77f)
                lineTo(8.91f, 12.7f)
                curveToRelative(0.05f, -0.23f, 0.09f, -0.46f, 0.09f, -0.7f)
                reflectiveCurveToRelative(-0.04f, -0.47f, -0.09f, -0.7f)
                lineToRelative(7.05f, -4.11f)
                curveToRelative(0.54f, 0.5f, 1.25f, 0.81f, 2.04f, 0.81f)
                curveToRelative(1.66f, 0f, 3f, -1.34f, 3f, -3f)
                reflectiveCurveToRelative(-1.34f, -3f, -3f, -3f)
                reflectiveCurveToRelative(-3f, 1.34f, -3f, 3f)
                curveToRelative(0f, 0.24f, 0.04f, 0.47f, 0.09f, 0.7f)
                lineTo(8.04f, 9.81f)
                curveTo(7.5f, 9.31f, 6.79f, 9f, 6f, 9f)
                curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f)
                reflectiveCurveToRelative(1.34f, 3f, 3f, 3f)
                curveToRelative(0.79f, 0f, 1.5f, -0.31f, 2.04f, -0.81f)
                lineToRelative(7.12f, 4.16f)
                curveToRelative(-0.05f, 0.21f, -0.08f, 0.43f, -0.08f, 0.65f)
                curveToRelative(0f, 1.61f, 1.31f, 2.92f, 2.92f, 2.92f)
                reflectiveCurveToRelative(2.92f, -1.31f, 2.92f, -2.92f)
                reflectiveCurveToRelative(-1.31f, -2.92f, -2.92f, -2.92f)
                close()
            }
            .build()
    }

    /** SkipNext（Rounded）。 */
    val SkipNext: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkipNext",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(7.58f, 16.89f)
                lineToRelative(5.77f, -4.07f)
                curveToRelative(0.56f, -0.4f, 0.56f, -1.24f, 0f, -1.63f)
                lineTo(7.58f, 7.11f)
                curveTo(6.91f, 6.65f, 6f, 7.12f, 6f, 7.93f)
                verticalLineToRelative(8.14f)
                curveToRelative(0f, 0.81f, 0.91f, 1.28f, 1.58f, 0.82f)
                close()
                moveTo(16f, 7f)
                verticalLineToRelative(10f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                reflectiveCurveToRelative(1f, -0.45f, 1f, -1f)
                verticalLineTo(7f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f)
                close()
            }
            .build()
    }

    /** SkipPrevious（Rounded）。 */
    val SkipPrevious: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkipPrevious",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(7f, 6f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                verticalLineToRelative(10f)
                curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
                reflectiveCurveToRelative(-1f, -0.45f, -1f, -1f)
                lineTo(6f, 7f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                close()
                moveTo(10.66f, 12.82f)
                lineToRelative(5.77f, 4.07f)
                curveToRelative(0.66f, 0.47f, 1.58f, -0.01f, 1.58f, -0.82f)
                lineTo(18.01f, 7.93f)
                curveToRelative(0f, -0.81f, -0.91f, -1.28f, -1.58f, -0.82f)
                lineToRelative(-5.77f, 4.07f)
                curveToRelative(-0.57f, 0.4f, -0.57f, 1.24f, 0f, 1.64f)
                close()
            }
            .build()
    }

    /** Speed（Rounded）。 */
    val Speed: ImageVector by lazy {
        ImageVector.Builder(
            name = "Speed",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(19.46f, 10f)
                arcToRelative(1f, 1f, 0f, false, false, -0.07f, 1f)
                arcToRelative(7.55f, 7.55f, 0f, false, true, 0.52f, 1.81f)
                arcToRelative(8f, 8f, 0f, false, true, -0.69f, 4.73f)
                arcToRelative(1f, 1f, 0f, false, true, -0.89f, 0.53f)
                horizontalLineTo(5.68f)
                arcToRelative(1f, 1f, 0f, false, true, -0.89f, -0.54f)
                arcTo(8f, 8f, 0f, false, true, 13f, 6.06f)
                arcToRelative(7.69f, 7.69f, 0f, false, true, 2.11f, 0.56f)
                arcToRelative(1f, 1f, 0f, false, false, 1f, -0.07f)
                arcToRelative(1f, 1f, 0f, false, false, -0.17f, -1.76f)
                arcTo(10f, 10f, 0f, false, false, 3.35f, 19f)
                arcToRelative(2f, 2f, 0f, false, false, 1.72f, 1f)
                horizontalLineToRelative(13.85f)
                arcToRelative(2f, 2f, 0f, false, false, 1.74f, -1f)
                arcToRelative(10f, 10f, 0f, false, false, 0.55f, -8.89f)
                arcToRelative(1f, 1f, 0f, false, false, -1.75f, -0.11f)
                close()
            }
            .path(fill = SolidColor(Color.Black)) {
                moveTo(10.59f, 12.59f)
                arcToRelative(2f, 2f, 0f, false, false, 2.83f, 2.83f)
                lineToRelative(5.66f, -8.49f)
                close()
            }
            .build()
    }

    /** StarBorder（Rounded）。 */
    val StarBorder: ImageVector by lazy {
        ImageVector.Builder(
            name = "StarBorder",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(19.65f, 9.04f)
                lineToRelative(-4.84f, -0.42f)
                lineToRelative(-1.89f, -4.45f)
                curveToRelative(-0.34f, -0.81f, -1.5f, -0.81f, -1.84f, 0f)
                lineTo(9.19f, 8.63f)
                lineToRelative(-4.83f, 0.41f)
                curveToRelative(-0.88f, 0.07f, -1.24f, 1.17f, -0.57f, 1.75f)
                lineToRelative(3.67f, 3.18f)
                lineToRelative(-1.1f, 4.72f)
                curveToRelative(-0.2f, 0.86f, 0.73f, 1.54f, 1.49f, 1.08f)
                lineToRelative(4.15f, -2.5f)
                lineToRelative(4.15f, 2.51f)
                curveToRelative(0.76f, 0.46f, 1.69f, -0.22f, 1.49f, -1.08f)
                lineToRelative(-1.1f, -4.73f)
                lineToRelative(3.67f, -3.18f)
                curveToRelative(0.67f, -0.58f, 0.32f, -1.68f, -0.56f, -1.75f)
                close()
                moveTo(12f, 15.4f)
                lineToRelative(-3.76f, 2.27f)
                lineToRelative(1f, -4.28f)
                lineToRelative(-3.32f, -2.88f)
                lineToRelative(4.38f, -0.38f)
                lineTo(12f, 6.1f)
                lineToRelative(1.71f, 4.04f)
                lineToRelative(4.38f, 0.38f)
                lineToRelative(-3.32f, 2.88f)
                lineToRelative(1f, 4.28f)
                lineTo(12f, 15.4f)
                close()
            }
            .build()
    }

    /** Sync（Rounded）。 */
    val Sync: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sync",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 4f)
                lineTo(12f, 2.21f)
                curveToRelative(0f, -0.45f, -0.54f, -0.67f, -0.85f, -0.35f)
                lineToRelative(-2.8f, 2.79f)
                curveToRelative(-0.2f, 0.2f, -0.2f, 0.51f, 0f, 0.71f)
                lineToRelative(2.79f, 2.79f)
                curveToRelative(0.32f, 0.31f, 0.86f, 0.09f, 0.86f, -0.36f)
                lineTo(12f, 6f)
                curveToRelative(3.31f, 0f, 6f, 2.69f, 6f, 6f)
                curveToRelative(0f, 0.79f, -0.15f, 1.56f, -0.44f, 2.25f)
                curveToRelative(-0.15f, 0.36f, -0.04f, 0.77f, 0.23f, 1.04f)
                curveToRelative(0.51f, 0.51f, 1.37f, 0.33f, 1.64f, -0.34f)
                curveToRelative(0.37f, -0.91f, 0.57f, -1.91f, 0.57f, -2.95f)
                curveToRelative(0f, -4.42f, -3.58f, -8f, -8f, -8f)
                close()
                moveTo(12f, 18f)
                curveToRelative(-3.31f, 0f, -6f, -2.69f, -6f, -6f)
                curveToRelative(0f, -0.79f, 0.15f, -1.56f, 0.44f, -2.25f)
                curveToRelative(0.15f, -0.36f, 0.04f, -0.77f, -0.23f, -1.04f)
                curveToRelative(-0.51f, -0.51f, -1.37f, -0.33f, -1.64f, 0.34f)
                curveTo(4.2f, 9.96f, 4f, 10.96f, 4f, 12f)
                curveToRelative(0f, 4.42f, 3.58f, 8f, 8f, 8f)
                verticalLineToRelative(1.79f)
                curveToRelative(0f, 0.45f, 0.54f, 0.67f, 0.85f, 0.35f)
                lineToRelative(2.79f, -2.79f)
                curveToRelative(0.2f, -0.2f, 0.2f, -0.51f, 0f, -0.71f)
                lineToRelative(-2.79f, -2.79f)
                curveToRelative(-0.31f, -0.31f, -0.85f, -0.09f, -0.85f, 0.36f)
                lineTo(12f, 18f)
                close()
            }
            .build()
    }

    /** TravelExplore（Rounded）。 */
    val TravelExplore: ImageVector by lazy {
        ImageVector.Builder(
            name = "TravelExplore",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(19.3f, 16.9f)
                curveToRelative(0.58f, -1.01f, 0.95f, -2.23f, 0.51f, -3.65f)
                curveToRelative(-0.53f, -1.72f, -2.04f, -3.05f, -3.84f, -3.22f)
                curveToRelative(-2.87f, -0.28f, -5.23f, 2.07f, -4.95f, 4.95f)
                curveToRelative(0.18f, 1.79f, 1.5f, 3.31f, 3.22f, 3.84f)
                curveToRelative(1.43f, 0.44f, 2.64f, 0.07f, 3.65f, -0.51f)
                lineToRelative(2.5f, 2.5f)
                curveToRelative(0.39f, 0.39f, 1.01f, 0.39f, 1.4f, 0f)
                lineToRelative(0f, 0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.01f, 0f, -1.4f)
                lineTo(19.3f, 16.9f)
                close()
                moveTo(15.5f, 17f)
                curveToRelative(-1.4f, 0f, -2.5f, -1.1f, -2.5f, -2.5f)
                reflectiveCurveToRelative(1.1f, -2.5f, 2.5f, -2.5f)
                reflectiveCurveToRelative(2.5f, 1.1f, 2.5f, 2.5f)
                reflectiveCurveTo(16.9f, 17f, 15.5f, 17f)
                close()
                moveTo(12f, 20f)
                verticalLineToRelative(2f)
                curveTo(6.48f, 22f, 2f, 17.52f, 2f, 12f)
                curveTo(2f, 6.48f, 6.48f, 2f, 12f, 2f)
                curveToRelative(4.84f, 0f, 8.87f, 3.44f, 9.8f, 8f)
                horizontalLineToRelative(-2.07f)
                curveToRelative(-0.64f, -2.46f, -2.4f, -4.47f, -4.73f, -5.41f)
                verticalLineTo(5f)
                curveToRelative(0f, 1.1f, -0.9f, 2f, -2f, 2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
                horizontalLineTo(8f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(3f)
                horizontalLineTo(9f)
                lineToRelative(-4.79f, -4.79f)
                curveTo(4.08f, 10.79f, 4f, 11.38f, 4f, 12f)
                curveTo(4f, 16.41f, 7.59f, 20f, 12f, 20f)
                close()
            }
            .build()
    }

    /** UnfoldLess（Rounded）。 */
    val UnfoldLess: ImageVector by lazy {
        ImageVector.Builder(
            name = "UnfoldLess",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(8.12f, 19.3f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                lineTo(12f, 16.83f)
                lineToRelative(2.47f, 2.47f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
                lineToRelative(-3.17f, -3.17f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                lineToRelative(-3.17f, 3.17f)
                curveToRelative(-0.4f, 0.38f, -0.4f, 1.02f, -0.01f, 1.41f)
                close()
                moveTo(15.88f, 4.7f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                lineTo(12f, 7.17f)
                lineTo(9.53f, 4.7f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0f, 1.42f)
                lineToRelative(3.17f, 3.17f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                lineToRelative(3.17f, -3.17f)
                curveToRelative(0.4f, -0.39f, 0.4f, -1.03f, 0.01f, -1.42f)
                close()
            }
            .build()
    }

    /** UnfoldMore（Rounded）。 */
    val UnfoldMore: ImageVector by lazy {
        ImageVector.Builder(
            name = "UnfoldMore",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 5.83f)
                lineToRelative(2.46f, 2.46f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
                lineTo(12.7f, 3.7f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                lineTo(8.12f, 6.88f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0f, 1.41f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                lineTo(12f, 5.83f)
                close()
                moveTo(12f, 18.17f)
                lineToRelative(-2.46f, -2.46f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0f, 1.41f)
                lineToRelative(3.17f, 3.18f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                lineToRelative(3.17f, -3.17f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                lineTo(12f, 18.17f)
                close()
            }
            .build()
    }

    /** VolumeOff（AutoMirrored.Rounded）。 */
    val VolumeOff: ImageVector by lazy {
        ImageVector.Builder(
            name = "VolumeOff",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = true,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(3.63f, 3.63f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0f, 1.41f)
                lineTo(7.29f, 8.7f)
                lineTo(7f, 9f)
                lineTo(4f, 9f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                verticalLineToRelative(4f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(3f)
                lineToRelative(3.29f, 3.29f)
                curveToRelative(0.63f, 0.63f, 1.71f, 0.18f, 1.71f, -0.71f)
                verticalLineToRelative(-4.17f)
                lineToRelative(4.18f, 4.18f)
                curveToRelative(-0.49f, 0.37f, -1.02f, 0.68f, -1.6f, 0.91f)
                curveToRelative(-0.36f, 0.15f, -0.58f, 0.53f, -0.58f, 0.92f)
                curveToRelative(0f, 0.72f, 0.73f, 1.18f, 1.39f, 0.91f)
                curveToRelative(0.8f, -0.33f, 1.55f, -0.77f, 2.22f, -1.31f)
                lineToRelative(1.34f, 1.34f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
                lineTo(5.05f, 3.63f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.42f, 0f)
                close()
                moveTo(19f, 12f)
                curveToRelative(0f, 0.82f, -0.15f, 1.61f, -0.41f, 2.34f)
                lineToRelative(1.53f, 1.53f)
                curveToRelative(0.56f, -1.17f, 0.88f, -2.48f, 0.88f, -3.87f)
                curveToRelative(0f, -3.83f, -2.4f, -7.11f, -5.78f, -8.4f)
                curveToRelative(-0.59f, -0.23f, -1.22f, 0.23f, -1.22f, 0.86f)
                verticalLineToRelative(0.19f)
                curveToRelative(0f, 0.38f, 0.25f, 0.71f, 0.61f, 0.85f)
                curveTo(17.18f, 6.54f, 19f, 9.06f, 19f, 12f)
                close()
                moveTo(10.29f, 5.71f)
                lineToRelative(-0.17f, 0.17f)
                lineTo(12f, 7.76f)
                lineTo(12f, 6.41f)
                curveToRelative(0f, -0.89f, -1.08f, -1.33f, -1.71f, -0.7f)
                close()
                moveTo(16.5f, 12f)
                curveToRelative(0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
                verticalLineToRelative(1.79f)
                lineToRelative(2.48f, 2.48f)
                curveToRelative(0.01f, -0.08f, 0.02f, -0.16f, 0.02f, -0.24f)
                close()
            }
            .build()
    }

    /** VolumeUp（AutoMirrored.Rounded）。 */
    val VolumeUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "VolumeUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = true,
        )
            .path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 10f)
                verticalLineToRelative(4f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(3f)
                lineToRelative(3.29f, 3.29f)
                curveToRelative(0.63f, 0.63f, 1.71f, 0.18f, 1.71f, -0.71f)
                lineTo(12f, 6.41f)
                curveToRelative(0f, -0.89f, -1.08f, -1.34f, -1.71f, -0.71f)
                lineTo(7f, 9f)
                lineTo(4f, 9f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                close()
                moveTo(16.5f, 12f)
                curveToRelative(0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
                verticalLineToRelative(8.05f)
                curveToRelative(1.48f, -0.73f, 2.5f, -2.25f, 2.5f, -4.02f)
                close()
                moveTo(14f, 4.45f)
                verticalLineToRelative(0.2f)
                curveToRelative(0f, 0.38f, 0.25f, 0.71f, 0.6f, 0.85f)
                curveTo(17.18f, 6.53f, 19f, 9.06f, 19f, 12f)
                reflectiveCurveToRelative(-1.82f, 5.47f, -4.4f, 6.5f)
                curveToRelative(-0.36f, 0.14f, -0.6f, 0.47f, -0.6f, 0.85f)
                verticalLineToRelative(0.2f)
                curveToRelative(0f, 0.63f, 0.63f, 1.07f, 1.21f, 0.85f)
                curveTo(18.6f, 19.11f, 21f, 15.84f, 21f, 12f)
                reflectiveCurveToRelative(-2.4f, -7.11f, -5.79f, -8.4f)
                curveToRelative(-0.58f, -0.23f, -1.21f, 0.22f, -1.21f, 0.85f)
                close()
            }
            .build()
    }

}
