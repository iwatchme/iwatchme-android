package com.iwatchme.locksim.jit

import com.iwatchme.locksim.SimObject

sealed class Op {
    data class NewObj(val tag: String, val klass: String) : Op()
    data class Lock(val tag: String) : Op()
    data class Unlock(val tag: String) : Op()
    data class Work(val cycles: Int = 1) : Op()
    data class WriteField(val container: String, val value: String) : Op()
    data class Return(val tag: String?) : Op()
    data class HandOff(val tag: String, val toThread: String) : Op()
}

class Method(val ops: List<Op>) {
    fun copy(newOps: List<Op>): Method = Method(newOps)
}

class MethodBuilder {
    private val ops = mutableListOf<Op>()
    fun newObj(local: String, klass: String = "java.lang.Object") {
        ops += Op.NewObj(local, klass)
    }
    fun lock(local: String) { ops += Op.Lock(local) }
    fun unlock(local: String) { ops += Op.Unlock(local) }
    fun work(cycles: Int = 1) { ops += Op.Work(cycles) }
    fun writeField(container: String, value: String) {
        ops += Op.WriteField(container, value)
    }
    fun handOff(local: String, toThread: String) {
        ops += Op.HandOff(local, toThread)
    }
    fun ret(local: String? = null) { ops += Op.Return(local) }
    fun build(): Method = Method(ops.toList())
}

fun method(block: MethodBuilder.() -> Unit): Method =
    MethodBuilder().apply(block).build()
