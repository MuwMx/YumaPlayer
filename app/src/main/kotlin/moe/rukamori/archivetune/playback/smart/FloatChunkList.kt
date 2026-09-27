package moe.rukamori.archivetune.playback.smart

internal class FloatChunkList(private val chunkSize: Int = 65536) {
    private val chunks = ArrayList<FloatArray>()
    private var currentChunk = FloatArray(chunkSize)
    private var currentPos = 0
    var totalCount: Int = 0
        private set

    fun add(value: Float) {
        if (currentPos >= chunkSize) {
            chunks.add(currentChunk)
            currentChunk = FloatArray(chunkSize)
            currentPos = 0
        }
        currentChunk[currentPos++] = value
        totalCount++
    }

    fun toFloatArray(): FloatArray {
        val result = FloatArray(totalCount)
        var destPos = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, result, destPos, chunk.size)
            destPos += chunk.size
        }
        if (currentPos > 0) {
            System.arraycopy(currentChunk, 0, result, destPos, currentPos)
        }
        return result
    }
}
