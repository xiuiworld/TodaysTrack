package dev.todaystrack.vehicle

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.todaystrack.vehicle.capture.JourneyCaptureService
import dev.todaystrack.vehicle.generation.*
import dev.todaystrack.vehicle.journey.*
import dev.todaystrack.vehicle.playback.TrackLibraryService
import dev.todaystrack.vehicle.storage.CaptureSession
import dev.todaystrack.vehicle.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : Activity(),SubmissionActions {
    private val app get()=application as TodaysTrackApplication
    private val prefs by lazy { getSharedPreferences("settings",MODE_PRIVATE) }
    private lateinit var screen:SubmissionScreen
    private var scope:CoroutineScope?=null
    private var car:Car?=null
    private var ux:CarUxRestrictionsManager?=null
    private var allowed=false
    private var loaded=false
    private var requesting=false
    private var selectedId:String?=null
    private var page=ScreenPage.HOME
    private var sessions:List<CaptureSession> = emptyList()
    private var open:CaptureSession?=null
    private var tracks:List<Track> = emptyList()
    private var intent:GenerationIntent?=null
    private var summary=SummaryInfo()
    private var message:String?=null
    private var captureStatus="기록 대기"
    private var counters=""
    private var restored=false
    private var editingDialog:AlertDialog?=null
    private val summaries=HashMap<String,SummaryInfo>()
    private val refreshLock=Mutex()
    private var controller:MediaController?=null
    private var controllerFuture:com.google.common.util.concurrent.ListenableFuture<MediaController>?=null
    private data class SummaryInfo(val distance:String="확인된 새 관측 없음",val details:String="",val hasFacts:Boolean=false)

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=Color.rgb(244,246,248);window.navigationBarColor=Color.rgb(244,246,248)
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        page=ScreenPage.entries.firstOrNull { it.name==savedInstanceState?.getString("page") } ?: ScreenPage.HOME
        selectedId=savedInstanceState?.getString("selection")
        screen=SubmissionScreen(this,this);screen.restoreScroll(savedInstanceState);setContentView(screen)
        car=Car.createCar(this,null,Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected,ready -> runOnUiThread {
            if(ready) runCatching {
                ux=connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                ux?.registerListener { restriction -> runOnUiThread { restrictions(!restriction.isRequiresDistractionOptimization) } }
                restrictions(ux?.currentCarUxRestrictions?.isRequiresDistractionOptimization==false)
            }.onFailure { restrictions(false) } else restrictions(false)
        } }
        render()
    }
    private fun restrictions(value:Boolean) {
        allowed=value
        if(!value) { editingDialog?.dismiss();editingDialog=null }
        render()
    }
    private fun controls():UiControls {
        val record=(listOfNotNull(open)+sessions).firstOrNull { it.id==selectedId }
        return UiControls(allowed,loaded,JourneyCaptureService.active.value,requesting,open!=null,
            record?.state=="SEALED",summary.hasFacts || intent!=null,intent?.state)
    }
    private fun kind(record:CaptureSession)=if(record.kind=="TRIP") "여행 기록" else "하루 기록"
    private fun lyrics(value:String)=if(value=="VOCAL") "가사 있는 곡" else "무가사"
    private fun item(record:CaptureSession):RecordItem {
        val time=Instant.ofEpochMilli(record.startedAtUtc).atZone(ZoneId.of(record.zoneId))
        return RecordItem(record.id,kind(record),time.format(DateTimeFormatter.ofPattern("MM월 dd일 · HH:mm:ss")),
            "${record.genre} · ${lyrics(record.lyricMode)}",record.state=="OPEN")
    }
    private fun defaultsLabel()="${if(prefs.getString("kind","DAY")=="TRIP") "여행 기록" else "하루 기록"} · ${prefs.getString("genre","잔잔한 팝")} · ${lyrics(prefs.getString("lyrics","INSTRUMENTAL")!!)}"
    private fun stateLabel():String = when(intent?.state) {
        null -> if(open?.id==selectedId && selectedId!=null) "이전 설정으로 기록을 이어갈 수 있습니다" else if(summary.hasFacts) "기록이 모였습니다 · 음악으로 남겨보세요" else "새 관측을 모아 음악으로 남겨보세요"
        "AVAILABLE" -> "곡이 저장되었습니다 · 아래에서 감상하세요"
        "LOCAL_PENDING","SENDING","PREPARING","TRANSPORT_PENDING" -> "생성 서버 연결 대기 · 기록과 입력은 보존됩니다"
        "ACCEPTANCE_UNCERTAIN" -> "접수 여부 확인 중 · 원래 작업만 조회하며 새로 생성하지 않습니다"
        "QUEUED","SUBMITTING","GENERATING","SERVER_PENDING" -> "음악 생성 작업 확인 중"
        "VALIDATING","READY" -> "음원 검사·차량 저장 중"
        "RECONCILING" -> "접수·결과 확인 중 · 자동으로 다시 생성하지 않습니다"
        "AUTH_REQUIRED" -> "로컬 시연 연결 등록을 확인한 뒤 기존 작업을 확인해 주세요"
        "BILLING_HELD" -> "생성 승인 대기 · 기록과 음악 방향은 보존됩니다"
        "MATERIAL_REVIEW_REQUIRED" -> "선택 재료 확인 필요 · 자동으로 제외하거나 다시 생성하지 않습니다"
        "REQUEST_HELD" -> "요청 확인 필요 · 입력과 기존 작업은 보존됩니다"
        "PLAN_READY" -> "재료 분석 완료 · 음악 방향을 확인해 주세요"
        "CANCELED" -> "작업이 취소되었습니다 · 기존 저장곡은 보존됩니다"
        "DELETED" -> "삭제된 작업입니다 · 자동으로 다시 생성하지 않습니다"
        else -> "작업이 보류되었습니다 · 기록과 기존 작업은 보존됩니다"
    }
    private fun render() {
        if(!::screen.isInitialized) return
        screen.render(SubmissionUiState(page,(listOfNotNull(open)+sessions).map(::item),selectedId,
            summary.distance,summary.details,stateLabel(),intent?.state,intent?.mode in setOf("FIXTURE","LOCAL_TEST"),captureStatus,
            JourneyCaptureService.active.value,controls(),tracks,defaultsLabel(),message))
    }
    private suspend fun readData()=refreshLock.withLock {
        val dao=app.database.captureDao()
        sessions=dao.sealedSessions();open=dao.openSession()
        val ids=(listOfNotNull(open)+sessions).map { it.id };val newest=sessions.firstOrNull()?.id
        selectedId=if(JourneyCaptureService.active.value && open!=null) open!!.id else RecordSelection.choose(ids,selectedId,
            prefs.getString("selected_session",null),newest,prefs.getString("last_seen_sealed",null))
        prefs.edit().putString("last_seen_sealed",newest).putString("selected_session",selectedId).apply()
        val key=selectedId
        tracks=app.database.generationDao().tracks()
        val record=(listOfNotNull(open)+sessions).firstOrNull { it.id==key }
        val nextIntent=key?.let { app.database.generationDao().forSession(it) }
        val info=if(record==null) SummaryInfo() else summaries[record.id] ?: run {
            val events=dao.events(record.id);val gaps=dao.gaps(record.id);val facts=events.map { it.toSummaryFact() }
            val result=JourneySummaryBuilder().build(facts,gaps.size,gaps.map { LocationGapBoundary(it.lastDurableSeq,it.firstAfterSeq,it.sourceMask) })
            val order=result.positionSegments.joinToString(" → [관측 공백] → ") { segment ->
                segment.positionSequence.fold(mutableListOf<String>()) { out,label -> out.apply { if(lastOrNull()!=label)add(label) } }.joinToString(" → ")
            }.ifEmpty { "확인된 새 위치 없음" }
            SummaryInfo("연속 관측 거리 %.2f km".format(result.continuousDistanceKm),
                "관측 순서 $order\n\n${events.size}개 SDK 이벤트 · 공백 ${gaps.size}개\n"+
                "구간 소요시간/운전 시간을 측정한 값이 아닙니다. 관측 공백 속 경로는 알 수 없습니다.\n"+
                "감정·방문 장소를 측정한 기록이 아닙니다.\n\n소스: ${events.map { it.source }.distinct().joinToString()}\n"+
                "원본 위치·시각·품질은 차량 내부 기록에 보존됩니다.",facts.count { it.quality in setOf("FRESH","VALID") }>1)
                .also { if(record.state=="SEALED") summaries[record.id]=it }
        }
        if(selectedId!=key) return@withLock
        val music=if(record==null) "" else musicDetails(record,nextIntent)
        if(selectedId!=key) return@withLock
        intent=nextIntent;summary=info.copy(details=info.details+music);loaded=true;render()
        if(!restored) { restored=true;screen.restoreAfterFirstRender() }
    }
    private suspend fun musicDetails(record: CaptureSession,frozen: GenerationIntent?): String {
        return runCatching {
            if(frozen!=null) {
                val envelope=dev.todaystrack.music.musicJson.parseToJsonElement(frozen.payload) as kotlinx.serialization.json.JsonObject
                if(envelope["schemaVersion"].toString()!="2") return@runCatching "\n\n기존 고정 입력과 원래 생성 작업으로 연결됩니다."
                val receipt=app.database.musicV2Dao().receipt(frozen.id)
                val direction=receipt?.plan?.let { VehicleMusicProjection.describePlan(dev.todaystrack.music.musicJson.parseToJsonElement(it) as kotlinx.serialization.json.JsonObject) }
                    ?: VehicleMusicProjection.description(frozen.payload)
                "\n\n$direction"+(frozen.errorCode?.let { "\n확인 상태: $it" } ?: "")+
                    (receipt?.returnedLyrics?.takeIf { it.isNotBlank() }?.let { "\n\n반환 가사 · 실제 가창 대조 전\n$it" } ?: "")
            } else if(record.state=="SEALED") {
                val dao=app.database.captureDao()
                val prefs=VehicleMusicProjection.history(dev.todaystrack.music.MusicPreferences(genre=record.genre,lyricMode=dev.todaystrack.music.LyricMode.valueOf(record.lyricMode)),app.database.musicV2Dao().recent())
                val preview=VehicleMusicProjection.freeze("preview",record,dao.events(record.id),dao.gaps(record.id),dao.epochsForSession(record.id),app.identity.deviceId,prefs)
                "\n\n미리보기 · "+VehicleMusicProjection.description(preview.payload)
            } else ""
        }.getOrElse { if(it is CancellationException) throw it;"\n\n음악 방향을 확인하지 못했습니다 · 원본 기록은 보존됩니다." }
    }
    private fun refresh() { scope?.launch { try { readData() } catch(c:CancellationException) { throw c } catch(e:Exception) {
        message="저장된 기록을 확인하지 못했습니다 · 기존 기록은 보존됩니다";render()
    } } }
    override fun page(page:ScreenPage) { this.page=page;render() }
    override fun select(id:String) {
        if(!controls().canSelect || (listOfNotNull(open)+sessions).none { it.id==id }) return
        selectedId=id;message=null;intent=null;summary=SummaryInfo();prefs.edit().putString("selected_session",id).apply();refresh()
    }
    override fun start() { if(controls().canStart) { message=null;beginCapture() } }
    private fun beginCapture() {
        if(!controls().canStart) return
        val required=arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION,"pleos.car.permission.FUSED_LOCATION")
        val missing=required.filter { checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED }
        if(missing.isNotEmpty()) { requestPermissions(missing.toTypedArray(),1);return }
        startForegroundService(Intent(this,JourneyCaptureService::class.java))
    }
    override fun onRequestPermissionsResult(code:Int,permissions:Array<out String>,grants:IntArray) {
        super.onRequestPermissionsResult(code,permissions,grants)
        if(code==1 && grants.isNotEmpty() && grants.all { it==PackageManager.PERMISSION_GRANTED } && controls().canStart) beginCapture()
    }
    override fun finish() { if(controls().canStop) startService(Intent(this,JourneyCaptureService::class.java).setAction(JourneyCaptureService.STOP)) }
    override fun closeScreen() { finishAndRemoveTask() }
    override fun generate() {
        if(!controls().canGenerate) return
        val selected=selectedId ?: return
        requesting=true;message=null;render()
        scope?.launch { try {
            val original=app.database.captureDao().session(selected)
            if(original.state!="SEALED") return@launch
            val frozen=GenerationIntentOutbox(app.database,app.identity).freeze(original.id)
            GenerationOutboxWorker.enqueue(this@MainActivity,original.id,frozen.id)
        } catch(c:CancellationException) { throw c } catch(e:Exception) {
            message="음악 만들기를 시작하지 못했습니다 · 기록과 기존 작업은 보존됩니다"
        } finally { requesting=false;refresh();render() } }
    }
    override fun play(id:String) {
        if(!allowed) return
        val track=tracks.firstOrNull { it.id==id } ?: return
        controller?.let { media -> media.setMediaItem(TrackLibraryService.mediaItem(track));media.prepare();media.play() }
    }
    override fun pause() { controller?.pause() }
    override fun details() {
        if(selectedId==null) return
        val record=(listOfNotNull(open)+sessions).firstOrNull { it.id==selectedId } ?: return
        showDialog("기록 상세",item(record).date+"\n${kind(record)} · ${record.genre} · ${lyrics(record.lyricMode)}\n\n"+
            summary.details+"\n\n$counters\n$captureStatus\n\n합성 주행 환경 · 실제 Pleos SDK 수집\n기본 표지는 실제 주행 사진이 아닙니다.")
    }
    private fun showDialog(title:String,text:String) {
        val dialog=AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("닫기",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply { minimumHeight=dp(76);minimumWidth=dp(76) } };dialog.show()
    }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    override fun settings() {
        if(!controls().canChangeSettings) return
        val fields=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(8),dp(24),dp(8)) }
        val values=linkedMapOf<String,Pair<Spinner,Array<String>>>()
        fun field(key:String,title:String,options:Array<String>,labels:Array<String>) {
            fields.addView(TextView(this).apply { text=title;textSize=18f;setTextColor(Color.rgb(100,113,125)) })
            val spinner=Spinner(this).apply {
                adapter=object:ArrayAdapter<String>(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,labels) {
                    override fun getDropDownView(position:Int,convertView:View?,parent:android.view.ViewGroup):View =
                        super.getDropDownView(position,convertView,parent).apply { minimumHeight=dp(76);(this as? TextView)?.textSize=20f }
                }
                setSelection(options.indexOf(prefs.getString(key,if(key=="genre") "잔잔한 팝" else options.first())).coerceAtLeast(0));contentDescription=title
            }
            fields.addView(spinner,LinearLayout.LayoutParams(-1,dp(76)));values[key]=spinner to options
        }
        field("kind","기록 범위",arrayOf("DAY","TRIP"),arrayOf("하루 기록","여행 기록"))
        field("genre","음악 장르",arrayOf("AUTO","잔잔한 팝","어쿠스틱","일렉트로닉","FUNK","DREAM_POP","ROCK","RNB","JAZZ","AMBIENT"),
            arrayOf("자동","팝","어쿠스틱","일렉트로닉","펑크","드림 팝","록","R&B","재즈","앰비언트"))
        field("lyrics","가사",arrayOf("INSTRUMENTAL","VOCAL"),arrayOf("무가사","가사 있는 곡"))
        val dialog=AlertDialog.Builder(this).setTitle("새 기록 설정").setView(fields).setNegativeButton("취소",null)
            .setPositiveButton("저장") { _,_ -> if(controls().canChangeSettings) {
                val edit=prefs.edit();values.forEach { (key,pair) -> edit.putString(key,pair.second[pair.first.selectedItemPosition]) };edit.apply();render()
            } }.create()
        editingDialog=dialog;dialog.setOnShowListener { for(button in listOf(AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE)) dialog.getButton(button).apply { minimumHeight=dp(76);minimumWidth=dp(76) } };dialog.show()
    }
    override fun info() {
        val dialog=AlertDialog.Builder(this).setTitle("오늘의 트랙 · ${BuildConfig.VERSION_NAME}")
            .setMessage("합성 주행 환경 · 실제 Pleos SDK 수집\n생성에는 PC 로컬 서버가 필요합니다. 저장곡은 오프라인으로 감상할 수 있습니다.\n정확한 위치는 차량에 남습니다. 사진·메모 없이 사용할 수 있습니다.\n표지는 고정 기본 이미지입니다.")
            .setPositiveButton("닫기",null).setNeutralButton("기기 연결 정보") { _,_ -> if(allowed) showDialog("개발 차량 공개 등록 정보",app.identity.publicRegistration().toString()) }.create()
        dialog.setOnShowListener { for(button in listOf(AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEUTRAL)) dialog.getButton(button).apply { minimumHeight=dp(76);minimumWidth=dp(76) };dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled=allowed };dialog.show()
    }
    private fun playback() {
        val media=controller
        fun clock(value:Long)="%d:%02d".format(value.coerceAtLeast(0)/60000,value.coerceAtLeast(0)/1000%60)
        val name=media?.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
        val label=when {
            media==null -> "재생기 연결 중"
            media.playerError!=null -> "재생을 시작할 수 없습니다 · 저장곡은 보존됩니다"
            media.currentMediaItem==null -> "저장된 곡을 선택해 주세요"
            media.playbackState==Player.STATE_BUFFERING -> "음원을 여는 중"
            media.playbackState==Player.STATE_ENDED -> "$name · 재생을 마쳤습니다"
            else -> "$name · ${if(media.isPlaying) "재생 중" else "일시정지"} ${clock(media.currentPosition)} / ${clock(media.duration)}"
        }
        screen.playback(PlaybackUiState(media?.currentMediaItem?.mediaId,label,media?.isPlaying==true))
    }
    override fun onStart() {
        super.onStart()
        scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate).also { s ->
            s.launch { GenerationOutboxWorker.recoverPending(this@MainActivity) }
            s.launch { JourneyCaptureService.status.collect { captureStatus=it;render() } }
            s.launch { JourneyCaptureService.active.collect { render();refresh() } }
            s.launch { combine(app.database.captureDao().count(),app.database.captureDao().lastEvent()) { count,event ->
                "이 차량 전체 저장 이벤트 ${count}개 · 마지막 관측 ${event?.receivedAtUtc?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")) } ?: "없음"}"
            }.collect { counters=it } }
            s.launch { while(isActive) { try { readData() } catch(c:CancellationException) { throw c } catch(e:Exception) { message="저장된 기록을 확인하지 못했습니다 · 기록은 보존됩니다";render() };delay(2000) } }
            s.launch { while(isActive) { playback();delay(1000) } }
        }
        val future=MediaController.Builder(this,SessionToken(this,ComponentName(this,TrackLibraryService::class.java))).buildAsync()
        controllerFuture=future
        future.addListener({ if(controllerFuture===future && scope!=null && !isFinishing) {
            controller=runCatching { future.get() }.getOrNull();controller?.addListener(object:Player.Listener { override fun onEvents(player:Player,events:Player.Events) { playback() } });playback()
        } },mainExecutor)
    }
    override fun onSaveInstanceState(out:Bundle) { super.onSaveInstanceState(out);out.putString("page",page.name);out.putString("selection",selectedId);screen.saveScroll(out) }
    override fun onStop() { editingDialog?.dismiss();editingDialog=null;scope?.cancel();scope=null;controllerFuture?.let { MediaController.releaseFuture(it) };controllerFuture=null;controller=null;super.onStop() }
    override fun onDestroy() { ux?.unregisterListener();car?.disconnect();super.onDestroy() }
}
