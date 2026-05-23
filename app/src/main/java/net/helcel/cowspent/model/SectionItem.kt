package net.helcel.cowspent.model

class SectionItem(var title: String) : Item {
    override fun isSection(): Boolean {
        return true
    }

}
