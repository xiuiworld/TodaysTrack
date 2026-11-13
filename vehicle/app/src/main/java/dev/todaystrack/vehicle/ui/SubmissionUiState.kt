package dev.todaystrack.vehicle.ui

import dev.todaystrack.vehicle.generation.Track

enum class ScreenPage(val label:String) { HOME("홈"), RECORDS("기록"), LIBRARY("보관함") }
data class RecordItem(val id:String,val title:String,val date:String,val settings:String,val open:Boolean)
data class SubmissionUiState(
    val page:ScreenPage=ScreenPage.HOME,
    val records:List<RecordItem> = emptyList(), val selectedId:String?=null,
    val distance:String="기록을 선택해 주세요", val details:String="",
    val status:String="저장된 기록을 확인하고 있습니다",val intentState:String?=null,val fixture:Boolean=false,
    val captureStatus:String="기록 대기",val capturing:Boolean=false,
    val controls:UiControls=UiControls(false,false,false,false,false,false,false,null),
    val tracks:List<Track> = emptyList(),val defaults:String="",val message:String?=null
)
data class PlaybackUiState(val trackId:String?=null,val label:String="저장된 곡을 선택해 주세요",val playing:Boolean=false)
interface SubmissionActions {
    fun page(page:ScreenPage)
    fun select(id:String)
    fun start()
    fun finish()
    fun closeScreen()
    fun settings()
    fun generate()
    fun details()
    fun play(id:String)
    fun pause()
    fun info()
}
