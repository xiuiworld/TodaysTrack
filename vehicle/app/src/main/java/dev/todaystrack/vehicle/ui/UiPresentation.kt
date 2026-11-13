package dev.todaystrack.vehicle.ui

/** Display-only descriptions. Eligibility and effects remain in UiControls/MainActivity. */
internal object UiPresentation {
    enum class Home { START, CAPTURE, SEALED, WORK, SAVED }
    fun home(state:SubmissionUiState):Home = when {
        state.capturing -> Home.CAPTURE
        state.records.none { it.id==state.selectedId } -> Home.START
        state.intentState=="AVAILABLE" -> Home.SAVED
        state.intentState!=null -> Home.WORK
        state.records.firstOrNull { it.id==state.selectedId }?.open==true -> Home.START
        else -> Home.SEALED
    }
    fun workTitle(state:String?)=when(state) {
        "ACCEPTANCE_UNCERTAIN" -> "접수 확인 중"
        "RECONCILING","REQUEST_HELD" -> "작업 확인 필요"
        "BILLING_HELD" -> "생성 승인 대기"
        "MATERIAL_REVIEW_REQUIRED" -> "재료 확인 필요"
        "PLAN_READY" -> "음악 방향 확인"
        "AUTH_REQUIRED" -> "연결 확인 필요"
        "CANCELED" -> "취소된 작업"
        "DELETED" -> "삭제된 작업"
        "VALIDATING","READY" -> "음원 저장 중"
        "LOCAL_PENDING","SENDING","PREPARING","TRANSPORT_PENDING","QUEUED","SUBMITTING","GENERATING","SERVER_PENDING" -> "생성 대기"
        else -> "작업 확인 필요"
    }
    fun source(provenance:String)=if(provenance in setOf("FIXTURE","LOCAL_TEST")) "대역 · 새 AI 생성 아님" else "새 AI 음악"
    fun clock(ms:Long?):String = ms?.takeIf { it>=0 }?.let { "%d:%02d".format(it/60000,it/1000%60) } ?: "--:--"
    fun distance(value:String?)=value?.removePrefix("연속 관측 거리 ")?.takeIf { it.endsWith(" km") } ?: "확인 전"
}
