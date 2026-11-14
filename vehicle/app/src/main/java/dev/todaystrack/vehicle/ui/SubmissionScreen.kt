package dev.todaystrack.vehicle.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.todaystrack.vehicle.R
import dev.todaystrack.vehicle.generation.Track

/** Native Views port of app-020. No database, generation, capture or media processing. */
class SubmissionScreen(context:Context,private val actions:SubmissionActions):LinearLayout(context) {
    private val kit=UiKit(context)
    private var model=SubmissionUiState()
    private var playbackState=PlaybackUiState()
    private var detailId:String?=null
    private var musicId:String?=null
    private var rendered=false
    private var route=""
    private var savedScroll=0
    private var restoreDetail:String?=null
    private var restoreMusic:String?=null
    private var confirmation:AlertDialog?=null
    private var confirmationRecord:String?=null
    private lateinit var scroll:ScrollView
    private lateinit var body:LinearLayout
    private lateinit var parked:TextView
    private lateinit var settings:View
    private lateinit var statusNotice:TextView
    private val nav=linkedMapOf<ScreenPage,FrameLayout>()
    private val toggles=mutableListOf<PlayToggle>()
    private val strips=mutableListOf<PlaybackStrip>()
    init {
        orientation=VERTICAL;setBackgroundColor(Color.WHITE)
        ViewCompat.setOnApplyWindowInsetsListener(this) { view,insets->
            // Pleos exposes its persistent bottom bar as mandatory system gestures.
            val safe=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.mandatorySystemGestures())
            view.setPadding(safe.left,safe.top,safe.right,safe.bottom);insets
        }
        chrome();renderBody()
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val widthDp=(MeasureSpec.getSize(widthMeasureSpec)-paddingLeft-paddingRight)/resources.displayMetrics.density
        val heightDp=(MeasureSpec.getSize(heightMeasureSpec)-paddingTop-paddingBottom)/resources.displayMetrics.density
        val scale=UiSizing.scale(widthDp,heightDp)
        if(scale!=kit.scale) { kit.scale=scale;chrome();renderBody() }
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
    }
    private fun chrome() {
        removeAllViews();nav.clear()
        val header=kit.row().apply { tag="app-header";setPadding(kit.unit(24),0,kit.unit(24),0) }
        header.addView(ImageView(context).apply { setImageResource(R.drawable.todays_track_mark);importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO },LayoutParams(kit.unit(36),kit.unit(36)))
        header.addView(kit.space(12));header.addView(kit.text("오늘의 트랙",kit.font*1.2f,true),LayoutParams(0,-2,1f))
        parked=kit.text("");header.addView(parked);header.addView(kit.space(8))
        val cluster=FrameLayout(context)
        cluster.addView(View(context).apply { background=kit.background(Color.WHITE,10,UiColors.border) },FrameLayout.LayoutParams(kit.unit(128),kit.unit(48),Gravity.CENTER))
        val icons=kit.row();val info=kit.action("앱 정보",76,quiet=true,icon="info") { actions.info() }
        settings=kit.action("새 기록 설정",76,quiet=true,icon="settings") { actions.settings() }
        (info.getChildAt(0) as ViewGroup).getChildAt(0).translationX=kit.unit(8).toFloat()
        (settings as ViewGroup).let { (it.getChildAt(0) as ViewGroup).getChildAt(0).translationX=-kit.unit(8).toFloat() }
        icons.addView(info);icons.addView(settings);cluster.addView(icons);header.addView(cluster)
        addView(header,LayoutParams(-1,kit.hit));addView(kit.line())
        statusNotice=kit.text("").apply {
            visibility=GONE;setPadding(kit.dp(24),kit.dp(8),kit.dp(24),kit.dp(8))
            accessibilityLiveRegion=ACCESSIBILITY_LIVE_REGION_POLITE
        }
        addView(statusNotice,LayoutParams(-1,-2))
        body=kit.column();scroll=ScrollView(context).apply { isFillViewport=true;scrollBarStyle=SCROLLBARS_OUTSIDE_OVERLAY;addView(body,FrameLayout.LayoutParams(-1,-1)) }
        addView(scroll,LayoutParams(-1,0,1f));addView(kit.line())
        val navigation=kit.row()
        ScreenPage.entries.forEach { page->
            val button=ButtonFrame(context).apply {
                minimumHeight=kit.hit;minimumWidth=kit.hit;isClickable=true;isFocusable=true;contentDescription=page.label
                setOnClickListener { detailId=null;musicId=null;actions.page(page) }
            }
            val face=kit.row().apply { gravity=Gravity.CENTER;tag="navigation-face" }
            face.addView(kit.icon(page.name.lowercase()));face.addView(kit.space(8));face.addView(kit.text(page.label,kit.font-2,true))
            button.addView(face,FrameLayout.LayoutParams(-1,-1))
            button.addView(View(context).apply { tag="nav-current";setBackgroundColor(UiColors.accent) },FrameLayout.LayoutParams(kit.unit(80),kit.dp(2),Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            navigation.addView(button,LayoutParams(0,kit.hit,1f));nav[page]=button
        }
        addView(navigation,LayoutParams(-1,kit.hit));updateChrome()
    }
    private fun updateChrome() {
        parked.text=if(model.controls.allowed) "정차 중" else "제한 확인 중";kit.enable(settings,model.controls.canChangeSettings)
        nav.forEach { (page,button)->
            button.isSelected=page==model.page;button.background=kit.background(Color.WHITE,0)
            button.findViewWithTag<View>("nav-current").visibility=if(button.isSelected) VISIBLE else INVISIBLE
        }
    }
    fun render(next:SubmissionUiState) {
        val changed=!rendered||next!=model;val changedSelection=next.selectedId!=model.selectedId
        model=next;rendered=true;updateChrome()
        if(changedSelection&&detailId!=null)detailId=next.selectedId
        if(confirmation!=null&&(!next.controls.canGenerate||next.selectedId!=confirmationRecord))dismissConfirmation()
        if(detailId!=null&&next.records.none { it.id==detailId })detailId=null
        if(musicId!=null&&next.tracks.none { it.id==musicId })musicId=null
        if(changed||route!=routeKey())renderBody() else updatePlayback()
    }
    private fun routeKey()="${model.page}:$detailId:$musicId"
    private fun renderBody() {
        val y=if(route==routeKey()) scroll.scrollY else 0;val focus=findFocus()?.contentDescription?.toString()
        route=routeKey();body.removeAllViews();toggles.clear();strips.clear()
        val collection=model.page==ScreenPage.RECORDS&&detailId==null||model.page==ScreenPage.LIBRARY&&musicId==null
        body.setPadding(kit.unit(24),kit.unit(if(model.page==ScreenPage.HOME||collection) 12 else 4),kit.unit(24),kit.unit(12))
        when(model.page) {
            ScreenPage.HOME -> home()
            ScreenPage.RECORDS -> model.records.firstOrNull { it.id==detailId }?.let(::recordDetail) ?: records()
            ScreenPage.LIBRARY -> model.tracks.firstOrNull { it.id==musicId }?.let(::musicDetail) ?: library()
        }
        updatePlayback();scroll.post { scroll.scrollTo(0,y) };if(focus!=null)findNamed(this,focus)?.requestFocus()
    }
    private fun findNamed(view:View,name:String):View? {
        if(view.contentDescription?.toString()==name)return view
        if(view is ViewGroup)for(index in 0 until view.childCount)findNamed(view.getChildAt(index),name)?.let { return it }
        return null
    }
    private fun selected()=model.records.firstOrNull { it.id==model.selectedId }
    private fun goRecord(id:String) {
        if(!model.controls.canSelect&&id!=model.selectedId)return
        if(id!=model.selectedId)actions.select(id)
        detailId=id;musicId=null
        if(model.page!=ScreenPage.RECORDS)actions.page(ScreenPage.RECORDS) else renderBody()
    }
    private fun goMusic(id:String) {
        val track=model.tracks.firstOrNull { it.id==id } ?: return
        if(!model.controls.canSelect&&track.sessionId!=model.selectedId)return
        musicId=id;detailId=null
        if(model.page!=ScreenPage.LIBRARY)actions.page(ScreenPage.LIBRARY) else renderBody()
    }
    private fun generate() {
        if(!model.controls.canGenerate)return
        if(model.intentState!=null) { actions.generate();return }
        val record=selected() ?: return
        val content=UiDialogs.body(context).apply {
            addView(kit.heading(record.title,true));addView(kit.space(4));addView(kit.text(record.date));addView(kit.space(12))
            addView(UiDialogs.row(context,"사용할 설정",record.settings));addView(UiDialogs.row(context,"전송할 정보","정밀 위치를 뺀 기록 요약"))
            addView(kit.space(12));addView(kit.text("요약은 생성 서버·음악 AI로 전송합니다."))
        }
        confirmationRecord=record.id
        confirmation=UiDialogs.show(context,"음악 만들기",content,"만들기",true) { if(model.controls.canGenerate&&model.selectedId==record.id)actions.generate() }
            .also { it.setOnDismissListener { confirmation=null;confirmationRecord=null } }
    }
    private fun dismissConfirmation() { confirmation?.dismiss();confirmation=null;confirmationRecord=null }
    private fun home() {
        val record=selected();val stage=UiPresentation.home(model)
        val row=kit.row().apply { tag="home-hero";gravity=Gravity.CENTER_VERTICAL;minimumHeight=kit.unit(220) };val copy=kit.column()
        val title=when(stage) { UiPresentation.Home.CAPTURE->"기록 중";UiPresentation.Home.SEALED->"기록 마감 완료";UiPresentation.Home.WORK->UiPresentation.workTitle(model.intentState);UiPresentation.Home.SAVED->"음악 저장 완료";else->if(!model.controls.loaded) "기록 확인 중" else if(record?.open==true) "마감 전 기록" else "새 기록" }
        copy.addView(kit.heading(title).apply { tag="home-title" });copy.addView(kit.space(12))
        val meta=kit.column().apply { tag="home-meta" }
        val subtitle=when(stage) { UiPresentation.Home.START->record?.settings ?: model.defaults;UiPresentation.Home.CAPTURE->model.distance;else->record?.date.orEmpty() }
        val summary=when(stage) { UiPresentation.Home.START->"";UiPresentation.Home.WORK->model.status;UiPresentation.Home.CAPTURE->record?.settings.orEmpty();else->model.distance+" · "+record?.settings.orEmpty() }
        fun metaLine(value:String)=kit.text(value).apply { maxLines=1;ellipsize=TextUtils.TruncateAt.END;if(value.isEmpty())visibility=INVISIBLE }
        // Three natural line boxes keep the action anchored as status/source text changes.
        meta.addView(metaLine(subtitle));meta.addView(kit.space(12));meta.addView(metaLine(summary))
        val indicator=when {
            stage==UiPresentation.Home.CAPTURE -> model.captureStatus
            model.fixture&&model.intentState!=null -> "대역 · 새 AI 생성 아님"
            else -> ""
        }
        meta.addView(kit.space(4));meta.addView(metaLine(indicator))
        copy.addView(meta,LayoutParams(-1,-2))
        copy.addView(kit.space(12));val buttons=kit.row()
        when(stage) {
            UiPresentation.Home.CAPTURE -> {
                buttons.addView(kit.action("기록 마감",primary=true) { actions.finish() }.also { kit.enable(it,model.controls.canStop) });buttons.addView(kit.space(8));buttons.addView(kit.action("화면 닫기",132) { actions.closeScreen() })
            }
            UiPresentation.Home.SAVED -> {
                buttons.addView(kit.action("보관함으로",primary=true) { detailId=null;musicId=null;actions.page(ScreenPage.LIBRARY) });buttons.addView(kit.space(8));buttons.addView(kit.action(if(model.controls.hasOpen) "기록 이어가기" else "새 기록 시작",176) { actions.start() }.also { kit.enable(it,model.controls.canStart) })
            }
            UiPresentation.Home.SEALED,UiPresentation.Home.WORK -> {
                buttons.addView(kit.action(if(stage==UiPresentation.Home.SEALED) "음악 만들기" else "기존 작업 확인",primary=true) { generate() }.also { kit.enable(it,model.controls.canGenerate) });buttons.addView(kit.space(8));buttons.addView(kit.action("기록 상세",132) { record?.let { goRecord(it.id) } })
            }
            else -> buttons.addView(kit.action(if(model.controls.hasOpen) "기록 이어가기" else "새 기록 시작",primary=true) { actions.start() }.also { kit.enable(it,model.controls.canStart) })
        }
        copy.addView(buttons)
        if(!model.controls.allowed) { copy.addView(kit.space(8));copy.addView(kit.text("차량 조작 제한 확인 중")) }
        row.addView(copy,LayoutParams(0,-2,1f));row.addView(kit.space(24))
        val music=kit.column().apply { tag="home-music";background=kit.startRule();setPadding(kit.unit(16),0,0,0) }
        val track=model.tracks.firstOrNull { it.id==playbackState.trackId } ?: model.tracks.firstOrNull { it.sessionId==record?.id } ?: model.tracks.firstOrNull()
        music.addView(kit.heading(if(track!=null&&track.sessionId!=record?.id) "이전에 만든 음악" else "음악",true));music.addView(kit.space(12))
        if(track==null) {
            music.addView(kit.icon("library"));music.addView(kit.space(12));music.addView(kit.text(if(model.controls.loaded) "저장된 음악이 없어요" else "저장된 음악 확인 중",kit.font));music.addView(kit.space(8));music.addView(kit.text(if(model.controls.loaded) "기록을 마친 뒤 음악을 만들 수 있습니다." else "보관함을 확인하고 있습니다."))
        } else {
            val album=kit.row();album.addView(kit.cover(track.sessionId,120));album.addView(kit.space(12))
            val text=kit.column();text.addView(kit.text(track.title,kit.font-2,true));text.addView(kit.space(4));text.addView(kit.text(model.records.firstOrNull { it.id==track.sessionId }?.date.orEmpty()));text.addView(kit.text(UiPresentation.source(track.provenance)))
            album.addView(text,LayoutParams(0,-2,1f));music.addView(album);music.addView(kit.space(12));music.addView(strip(track))
        }
        row.addView(music,LayoutParams(0,-2,1f));body.addView(row,LayoutParams(-1,0,1f));body.addView(kit.space(16));body.addView(kit.line())
        val latest=model.records.firstOrNull();val recent=ButtonFrame(context).apply {
            tag="home-recent"
            minimumHeight=kit.hit;isClickable=true;isFocusable=true;contentDescription="기록 상세 보기"
            setOnClickListener { if(latest!=null)goRecord(latest.id) else actions.page(ScreenPage.RECORDS) }
        }
        val recentCopy=kit.row();val labels=kit.column();labels.addView(kit.text(latest?.let { it.title+if(it.open) (if(model.capturing) " · 기록 중" else " · 마감 전") else "" } ?: if(model.controls.loaded) "아직 기록이 없어요" else "기록 확인 중",kit.font-2,true))
        latest?.let { labels.addView(kit.text(it.date+(it.distance?.let { distance->" · "+UiPresentation.distance(distance) } ?: ""))) }
        recentCopy.addView(labels,LayoutParams(0,-2,1f));recentCopy.addView(kit.icon("chevron"));recent.addView(recentCopy,FrameLayout.LayoutParams(-1,-1));body.addView(recent,LayoutParams(-1,kit.hit))
    }
    private fun collectionHeading(title:String,count:String,recordAction:Boolean=false) {
        val heading=kit.row().apply { minimumHeight=kit.hit }
        heading.addView(kit.heading(title));heading.addView(kit.space(12));heading.addView(kit.text(count));heading.addView(Space(context),LayoutParams(0,1,1f))
        if(recordAction)heading.addView(kit.action(if(model.controls.hasOpen) "기록 이어가기" else "새 기록 시작",176) { actions.start() }.also { kit.enable(it,model.controls.canStart) })
        body.addView(heading);body.addView(kit.space(8))
    }
    private fun records() {
        collectionHeading("기록",if(model.controls.loaded) "${model.records.size}개" else "확인 중",true)
        if(model.records.isEmpty()) {
            if(!model.controls.loaded)empty("기록 확인 중",model.message ?: "저장된 기록을 확인하고 있습니다.")
            else empty("아직 기록이 없어요","첫 기록을 시작해 보세요.")
            return
        }
        model.records.forEachIndexed { index,record->
            val track=model.tracks.firstOrNull { it.sessionId==record.id }
            val row=collectionBody(record.id,record.title,record.date,track?.let { "만든 음악 · ${it.title}" } ?: if(record.open) (if(model.capturing&&record.id==model.selectedId) "기록 중" else "마감 전") else if(record.id==model.selectedId&&model.intentState!=null) UiPresentation.workTitle(model.intentState) else "만든 음악 없음",track!=null)
            row.addView(kit.space(16));val tail=tail()
            tail.addView(kit.text(UiPresentation.distance(record.distance)).apply { setSingleLine();ellipsize=TextUtils.TruncateAt.END },LayoutParams(0,-2,1f))
            tail.addView(FrameLayout(context).apply { addView(kit.icon("chevron"),FrameLayout.LayoutParams(kit.unit(24),kit.unit(24),Gravity.CENTER)) },LayoutParams(kit.hit,kit.hit));row.addView(tail)
            val button=ButtonFrame(context).apply { contentDescription="기록 상세 · ${record.title} · ${record.date}";isFocusable=true;isClickable=true;setOnClickListener { goRecord(record.id) } }
            kit.enable(button,model.controls.canSelect||record.id==model.selectedId)
            button.addView(row,FrameLayout.LayoutParams(-1,-1));body.addView(button,LayoutParams(-1,rowHeight()));body.addView(kit.line())
            if(index<model.records.lastIndex)body.addView(kit.space(8))
        }
    }
    private fun library() {
        collectionHeading("보관함",if(model.controls.loaded) "${model.tracks.size}곡" else "확인 중")
        if(model.tracks.isEmpty()) {
            if(!model.controls.loaded)empty("보관함 확인 중",model.message ?: "저장된 음악을 확인하고 있습니다.")
            else { empty("아직 저장된 음악이 없어요","기록을 마친 뒤 음악을 만들어 보세요.");body.addView(kit.action("기록 보러 가기") { actions.page(ScreenPage.RECORDS) }) }
            return
        }
        model.tracks.forEachIndexed { index,track->
            val record=model.records.firstOrNull { it.id==track.sessionId }
            val root=kit.row();val content=collectionBody(track.sessionId,track.title,(record?.title ?: "원래 기록")+" · "+record?.date.orEmpty(),UiPresentation.source(track.provenance))
            val button=ButtonFrame(context).apply { contentDescription="음악 상세 · ${track.title} · ${record?.date.orEmpty()}";isClickable=true;isFocusable=true;setOnClickListener { goMusic(track.id) } }
            kit.enable(button,model.controls.canSelect||track.sessionId==model.selectedId)
            button.addView(content,FrameLayout.LayoutParams(-1,-1));root.addView(button,LayoutParams(0,rowHeight(),1f));root.addView(kit.space(16))
            val tail=tail();tail.addView(kit.text(UiPresentation.clock(track.durationMs.takeIf { it>0 })).apply { setSingleLine();gravity=Gravity.END },LayoutParams(0,-2,1f));tail.addView(toggle(track));root.addView(tail)
            body.addView(root,LayoutParams(-1,rowHeight()));body.addView(kit.line())
            if(index<model.tracks.lastIndex)body.addView(kit.space(8))
        }
    }
    private fun rowHeight()=maxOf((kit.hit*1.5f).toInt(),kit.unit(60)+kit.unit(24),kit.dp(kit.font*4.8f)+kit.unit(24))
    private fun tail()=kit.row().apply { gravity=Gravity.END or Gravity.CENTER_VERTICAL;layoutParams=LayoutParams(kit.unit(152),kit.hit) }
    private fun collectionBody(id:String,title:String,subtitle:String,note:String,hasCover:Boolean=true):LinearLayout = kit.row().apply {
        setPadding(0,kit.unit(12),0,kit.unit(12));addView(if(hasCover) kit.cover(id,60) else kit.placeholder(60,"records"));addView(kit.space(16))
        val text=kit.column().apply { minimumHeight=kit.dp(kit.font*4.8f) }
        text.addView(kit.text(title,kit.font-2,true).apply { tag="collection-title";maxLines=1;ellipsize=TextUtils.TruncateAt.END });text.addView(kit.space(4))
        text.addView(kit.text(subtitle).apply { maxLines=1;ellipsize=TextUtils.TruncateAt.END });text.addView(kit.space(4));text.addView(kit.text(note).apply { maxLines=1;ellipsize=TextUtils.TruncateAt.END })
        addView(text,LayoutParams(0,-2,1f))
    }
    private fun detailHeading(title:String,backLabel:String,extra:View,onBack:()->Unit) {
        val row=kit.row();row.addView(kit.action(backLabel,76,quiet=true,icon="back",onClick=onBack));row.addView(kit.space(4));row.addView(kit.heading(title),LayoutParams(0,-2,1f));row.addView(kit.space(4));row.addView(extra);body.addView(row);body.addView(kit.space(8))
    }
    private fun recordDetail(record:RecordItem) {
        detailHeading(record.title,"기록 목록으로 돌아가기",kit.action("기록 정보",76,quiet=true,icon="more") { actions.details() }) { detailId=null;renderBody() }
        body.addView(kit.space(4))
        val stats=kit.row().apply { setPadding(0,kit.unit(12),0,kit.unit(12)) }
        val time=kit.column();time.addView(kit.text("기록 시각"));time.addView(kit.space(4));time.addView(kit.text(record.date,kit.font-2,true));stats.addView(time,LayoutParams(0,-2,1f));stats.addView(kit.space(24))
        val distance=kit.column().apply { background=kit.startRule();setPadding(kit.unit(16),0,0,0) };distance.addView(kit.text("연속 관측 거리"));distance.addView(kit.space(4));distance.addView(kit.heading(UiPresentation.distance(if(record.id==model.selectedId) model.distance else record.distance),true));stats.addView(distance,LayoutParams(0,-2,1f));body.addView(stats)
        body.addView(kit.space(12));body.addView(kit.line());body.addView(kit.space(16));body.addView(kit.heading("이 기록의 음악",true));body.addView(kit.space(12))
        val tracks=model.tracks.filter { it.sessionId==record.id }
        if(tracks.isNotEmpty())tracks.forEach { track->
            val row=kit.row();row.addView(kit.cover(record.id,120));row.addView(kit.space(16))
            val copy=kit.column();copy.addView(kit.heading(track.title,true));copy.addView(kit.space(4));copy.addView(kit.text(record.settings+" · "+UiPresentation.clock(track.durationMs.takeIf { it>0 })));copy.addView(kit.space(4));copy.addView(kit.text(UiPresentation.source(track.provenance)))
            row.addView(copy,LayoutParams(0,-2,1f));row.addView(kit.space(16));row.addView(toggle(track));body.addView(row)
        } else {
            val pending=record.id==model.selectedId&&model.intentState!=null
            val row=kit.row();row.addView(kit.placeholder(120,"library"));row.addView(kit.space(16))
            val copy=kit.column();copy.addView(kit.text(if(record.open) (if(model.capturing) "기록을 모으고 있어요" else "마감 전 기록이에요") else if(pending) UiPresentation.workTitle(model.intentState) else "아직 만든 음악이 없어요",kit.font-2,true));copy.addView(kit.space(4));copy.addView(kit.text(if(pending) model.status else record.settings))
            if(pending&&model.fixture)copy.addView(kit.text("대역 · 새 AI 생성 아님"))
            if(record.open&&model.capturing)copy.addView(kit.text(model.captureStatus))
            row.addView(copy,LayoutParams(0,-2,1f));row.addView(kit.space(16))
            val action=if(record.open) kit.action(if(model.capturing) "기록 마감" else "기록 이어가기",176) { if(model.capturing)actions.finish() else actions.start() }.also { kit.enable(it,if(model.capturing) model.controls.canStop else model.controls.canStart) }
            else kit.action(if(pending) "상태 확인" else "이 기록으로 만들기",200,primary=!pending) { generate() }.also { kit.enable(it,model.controls.canGenerate&&record.id==model.selectedId) }
            row.addView(action);body.addView(row)
        }
    }
    private fun musicDetail(track:Track) {
        val record=model.records.firstOrNull { it.id==track.sessionId }
        val link=kit.action("원래 기록 보기",176,quiet=true) { goRecord(track.sessionId) }
        (link.getChildAt(0) as FrameLayout).let { face->
            face.removeAllViews();val label=kit.row().apply { gravity=Gravity.CENTER }
            label.addView(kit.text("기록 보기",kit.font-2,true));label.addView(kit.space(8))
            label.addView(kit.icon("chevron"),LayoutParams(kit.unit(16),kit.unit(16)))
            face.addView(label,FrameLayout.LayoutParams(-1,-1))
        }
        kit.enable(link,record!=null&&(model.controls.canSelect||record.id==model.selectedId))
        detailHeading("음악 상세","보관함으로 돌아가기",link) { musicId=null;renderBody() }
        val row=kit.row();row.addView(kit.cover(track.sessionId,240).apply { tag="album-cover-large" });row.addView(kit.space(24))
        val copy=kit.column();copy.addView(kit.heading(track.title));copy.addView(kit.space(12));copy.addView(kit.text(record?.settings.orEmpty()));copy.addView(kit.space(12));copy.addView(kit.text(UiPresentation.source(track.provenance)));copy.addView(kit.space(20));copy.addView(kit.text((record?.title ?: "원래 기록")+"에서 만든 음악"));copy.addView(kit.space(4));copy.addView(kit.text(record?.date.orEmpty()));copy.addView(kit.space(8))
        row.addView(copy,LayoutParams(0,-2,1f));body.addView(row);body.addView(kit.space(8));body.addView(kit.line());body.addView(strip(track))
    }
    private fun empty(title:String,description:String) { body.addView(kit.space(24));body.addView(kit.icon("library"));body.addView(kit.space(12));body.addView(kit.heading(title,true));body.addView(kit.space(8));body.addView(kit.text(description)) }
    private fun toggle(track:Track)=PlayToggle(kit,track,actions).also { toggles+=it }
    private fun strip(track:Track)=PlaybackStrip(kit,track,actions).also { strips+=it }
    private fun updatePlayback() {
        val notice=listOfNotNull(model.message,playbackState.notice).distinct().joinToString("\n")
        if(statusNotice.text.toString()!=notice)statusNotice.text=notice
        statusNotice.visibility=if(notice.isEmpty()) GONE else VISIBLE
        toggles.forEach { it.bind(model,playbackState) };strips.forEach { it.bind(model,playbackState) }
    }
    fun playback(state:PlaybackUiState) {
        val changedTrack=state.trackId!=playbackState.trackId;playbackState=state
        if(changedTrack&&model.page==ScreenPage.HOME)renderBody() else updatePlayback()
    }
    fun back():Boolean = when {
        confirmation!=null -> { dismissConfirmation();true }
        musicId!=null -> { musicId=null;renderBody();true }
        detailId!=null -> { detailId=null;renderBody();true }
        else -> false
    }
    fun saveScroll(out:Bundle) { out.putInt("panel_scroll",scroll.scrollY);out.putString("ui_record_detail",detailId);out.putString("ui_music_detail",musicId) }
    fun restoreScroll(state:Bundle?) { savedScroll=state?.getInt("panel_scroll") ?: 0;restoreDetail=state?.getString("ui_record_detail");restoreMusic=state?.getString("ui_music_detail") }
    fun restoreAfterFirstRender() {
        detailId=restoreDetail?.takeIf { id->id==model.selectedId&&model.records.any { it.id==id } };musicId=restoreMusic?.takeIf { id->model.tracks.any { it.id==id } }
        renderBody();scroll.post { scroll.scrollTo(0,savedScroll) }
    }
    override fun onDetachedFromWindow() { dismissConfirmation();super.onDetachedFromWindow() }
}
