package net.helcel.cowspent.util

/**
 * Callback
 */
interface ICallback {
    fun onFinish()
    fun onFinish(result: String, message: String)
    fun onScheduled()
}
