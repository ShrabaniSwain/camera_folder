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

    //Live
    const val Interstitial_Ad_ID = "ca-app-pub-8665801057843551/3988955668"
//    const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-8665801057843551/7529579575"

    //Test
//    const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
//    const val Interstitial_Ad_ID = "ca-app-pub-3940256099942544/1033173712"
//    const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/3419835294"

}