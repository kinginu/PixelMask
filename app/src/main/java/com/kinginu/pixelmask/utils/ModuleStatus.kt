package com.kinginu.pixelmask.utils

object ModuleStatus {
    @JvmField
    @Volatile
    var hookedFlag: Boolean = false

    @JvmStatic
    fun isModuleActive(): Boolean = hookedFlag
}
