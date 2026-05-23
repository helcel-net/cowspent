package net.helcel.cowspent.util

/**
 * Callback
 */
interface IProjectCreationCallback {
    fun onFinish(result: String, message: String, usePrivateApi: Boolean)

}
