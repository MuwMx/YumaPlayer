package moe.rukamori.archivetune.ui.component.splash

import android.content.Context
import android.graphics.Path
import androidx.core.graphics.PathParser
import org.xmlpull.v1.XmlPullParser

object SplashVectorLoader {
    private val pathCache = HashMap<Int, Path>()

    fun loadPath(context: Context, resId: Int): Path {
        pathCache[resId]?.let { return it }

        val combinedPath = Path()
        try {
            val parser = context.resources.getXml(resId)
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "path") {
                    for (i in 0 until parser.attributeCount) {
                        val attrName = parser.getAttributeName(i)
                        if (attrName == "pathData" || attrName == "android:pathData") {
                            val pathData = parser.getAttributeValue(i)
                            if (!pathData.isNullOrBlank()) {
                                try {
                                    val subPath = PathParser.createPathFromPathData(pathData)
                                    combinedPath.addPath(subPath)
                                } catch (_: Exception) {
                                }
                            }
                            break
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
        }

        pathCache[resId] = combinedPath
        return combinedPath
    }
}
