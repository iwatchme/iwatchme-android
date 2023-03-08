package com.iwatchme.startuplauncher.sort

import java.lang.IllegalStateException
import java.util.*

class Graph {

    constructor(vertexCount: Int) {
        this.vertexCount = vertexCount
        adj = Array(vertexCount) {
            LinkedList<Int>()
        }

    }


    var vertexCount: Int = 0

    var adj: Array<LinkedList<Int>>


    fun addEdge(u: Int, v: Int) {

        adj[u].add(v)
    }

    fun topoligicalSort(): Vector<Int> {
        var indegree: Array<Int> = Array(vertexCount) {
            0
        }
        var queue: Queue<Int> = LinkedList()
        var ret: Vector<Int> = Vector()

        for (i in 0 until vertexCount) {
            adj[i].forEach {
                indegree[it]++
            }
        }
3
        indegree.forEachIndexed { index, i ->
            if (i == 0) {
                queue.add(index)
            }
        }

        var cnt = 0

        while (!queue.isEmpty()) {
            var u = queue.poll()

            ret.add(u)

            adj[u].forEach { value ->
                if (--indegree[value] == 0) {
                    queue.add(value)
                }
            }
            cnt++
        }

        if (cnt != vertexCount) {
            throw IllegalStateException("exist a cycle in graph")
        }

        return ret


    }
}