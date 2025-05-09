package com.app.camerademo

object Util {

    fun getFileNumber() {
        val time = System.currentTimeMillis()
        val initial = time.toString().substring(0, 2).toInt()
        val remaining = time.toString().substring(2)
        val by = initial / 10
        val rem = initial % 10
        val last = "$rem$by$remaining$initial"
        println("last: $last")
        println("rem : $rem")
        println("by  : $by")
        println("remm: $remaining")
        println("init: $initial")
        println("time: $time")
    }

}