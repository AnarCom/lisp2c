package ru.nsu.lisp2c

class MapStack<K, V> {
    private val maps: MutableList<MutableMap<K, V>> = mutableListOf(mutableMapOf())

    fun pushScope(){
        maps.add(mutableMapOf())
    }

    fun popScope(){
        maps.removeLast()
    }

    operator fun get(key: K): V?{
        return maps.findLast { it.containsKey(key) }?.get(key)
    }


    operator fun set(key: K, value: V){
        maps.last()[key] = value
    }
}
