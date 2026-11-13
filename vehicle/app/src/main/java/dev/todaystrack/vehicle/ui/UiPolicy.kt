package dev.todaystrack.vehicle.ui

data class UiControls(val allowed:Boolean,val loaded:Boolean,val capturing:Boolean,val requesting:Boolean,
    val hasOpen:Boolean,val sealed:Boolean,val hasFacts:Boolean,val intentState:String?) {
    val canStart get() = allowed && loaded && !capturing && !requesting
    val canStop get() = capturing
    val canSelect get() = allowed && loaded && !capturing && !requesting
    val canChangeSettings get() = canStart && !hasOpen
    val canGenerate get() = allowed && loaded && !capturing && !requesting && sealed && hasFacts && intentState!="AVAILABLE"
}

object RecordSelection {
    fun choose(ids:List<String>,current:String?,saved:String?,newest:String?,lastSeen:String?):String? =
        newest?.takeIf { it!=lastSeen && it in ids } ?: current?.takeIf { it in ids }
        ?: saved?.takeIf { it in ids } ?: ids.firstOrNull()
}
