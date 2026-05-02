package com.yogaflow.flow

class FlowPlaylistEngine {

    private var flows: List<YogaFlow> = emptyList()
    private var currentIndex: Int = 0

    fun setPlaylist(newFlows: List<YogaFlow>) {
        flows = newFlows
        currentIndex = 0
    }

    fun current(): YogaFlow? {
        return flows.getOrNull(currentIndex)
    }

    fun currentNumber(): Int {
        return if (flows.isEmpty()) 0 else currentIndex + 1
    }

    fun total(): Int {
        return flows.size
    }

    fun moveNext(): YogaFlow? {
        if (currentIndex < flows.lastIndex) {
            currentIndex++
            return flows[currentIndex]
        }
        return null
    }

    fun reset() {
        currentIndex = 0
    }

    fun isLastFlow(): Boolean {
        return flows.isNotEmpty() && currentIndex >= flows.lastIndex
    }

    fun isEmpty(): Boolean {
        return flows.isEmpty()
    }
}
