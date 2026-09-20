package moe.rukamori.archivetune.core.common.math

fun stackBlurPixels(
    pixels: IntArray,
    width: Int,
    height: Int,
    radius: Int,
) {
    val wm = width - 1
    val hm = height - 1
    val div = radius + radius + 1
    val red = IntArray(width * height)
    val green = IntArray(width * height)
    val blue = IntArray(width * height)
    val vMin = IntArray(max(width, height))
    val divSum = ((div + 1) shr 1).let { it * it }
    val divTable = IntArray(256 * divSum) { it / divSum }
    val stack = Array(div) { IntArray(3) }

    var yi = 0
    var yw = 0
    for (y in 0 until height) {
        var rinsum = 0
        var ginsum = 0
        var binsum = 0
        var routsum = 0
        var goutsum = 0
        var boutsum = 0
        var rsum = 0
        var gsum = 0
        var bsum = 0

        for (i in -radius..radius) {
            val p = pixels[yi + i.coerceIn(0, wm)]
            val sir = stack[i + radius]
            sir[0] = p shr 16 and 0xFF
            sir[1] = p shr 8 and 0xFF
            sir[2] = p and 0xFF
            val rbs = radius + 1 - abs(i)
            rsum += sir[0] * rbs
            gsum += sir[1] * rbs
            bsum += sir[2] * rbs
            if (i > 0) {
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
            } else {
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
            }
        }

        var stackPointer = radius
        for (x in 0 until width) {
            red[yi] = divTable[rsum]
            green[yi] = divTable[gsum]
            blue[yi] = divTable[bsum]

            rsum -= routsum
            gsum -= goutsum
            bsum -= boutsum

            val stackStart = (stackPointer - radius + div) % div
            val sir = stack[stackStart]

            routsum -= sir[0]
            goutsum -= sir[1]
            boutsum -= sir[2]

            if (y == 0) {
                vMin[x] = (x + radius + 1).coerceAtMost(wm)
            }
            val p = pixels[yw + vMin[x]]
            sir[0] = p shr 16 and 0xFF
            sir[1] = p shr 8 and 0xFF
            sir[2] = p and 0xFF

            rinsum += sir[0]
            ginsum += sir[1]
            binsum += sir[2]

            rsum += rinsum
            gsum += ginsum
            bsum += binsum

            stackPointer = (stackPointer + 1) % div
            val nextSir = stack[stackPointer]

            routsum += nextSir[0]
            goutsum += nextSir[1]
            boutsum += nextSir[2]

            rinsum -= nextSir[0]
            ginsum -= nextSir[1]
            binsum -= nextSir[2]
            yi++
        }
        yw += width
    }

    for (x in 0 until width) {
        var rinsum = 0
        var ginsum = 0
        var binsum = 0
        var routsum = 0
        var goutsum = 0
        var boutsum = 0
        var rsum = 0
        var gsum = 0
        var bsum = 0
        var yp = -radius * width

        for (i in -radius..radius) {
            val yiIndex = max(0, yp) + x
            val sir = stack[i + radius]
            sir[0] = red[yiIndex]
            sir[1] = green[yiIndex]
            sir[2] = blue[yiIndex]
            val rbs = radius + 1 - abs(i)
            rsum += red[yiIndex] * rbs
            gsum += green[yiIndex] * rbs
            bsum += blue[yiIndex] * rbs
            if (i > 0) {
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
            } else {
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
            }
            if (i < hm) yp += width
        }

        var yiIndex = x
        var stackPointer = radius
        for (y in 0 until height) {
            pixels[yiIndex] =
                pixels[yiIndex] and -0x1000000 or
                (divTable[rsum] shl 16) or
                (divTable[gsum] shl 8) or
                divTable[bsum]

            rsum -= routsum
            gsum -= goutsum
            bsum -= boutsum

            val stackStart = (stackPointer - radius + div) % div
            val sir = stack[stackStart]

            routsum -= sir[0]
            goutsum -= sir[1]
            boutsum -= sir[2]

            if (x == 0) {
                vMin[y] = ((y + radius + 1).coerceAtMost(hm)) * width
            }
            val p = x + vMin[y]
            sir[0] = red[p]
            sir[1] = green[p]
            sir[2] = blue[p]

            rinsum += sir[0]
            ginsum += sir[1]
            binsum += sir[2]

            rsum += rinsum
            gsum += ginsum
            bsum += binsum

            stackPointer = (stackPointer + 1) % div
            val nextSir = stack[stackPointer]

            routsum += nextSir[0]
            goutsum += nextSir[1]
            boutsum += nextSir[2]

            rinsum -= nextSir[0]
            ginsum -= nextSir[1]
            binsum -= nextSir[2]

            yiIndex += width
        }
    }
}

private inline fun abs(value: Int): Int = if (value < 0) -value else value
private inline fun max(a: Int, b: Int): Int = if (a > b) a else b
