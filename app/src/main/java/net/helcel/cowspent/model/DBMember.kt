package net.helcel.cowspent.model

import java.io.Serializable

class DBMember(
    var id: Long,
    var remoteId: Long,
    var projectId: Long,
    var name: String,
    var isActivated: Boolean,
    var weight: Double,
    var state: Int,
    var r: Int?,
    var g: Int?,
    var b: Int?,
    var ncUserId: String?,
    var avatar: String?
) : Serializable {

    override fun toString(): String {
        return "#DBMember$id/$remoteId,$name, p$projectId, $weight, $isActivated"
    }
}
