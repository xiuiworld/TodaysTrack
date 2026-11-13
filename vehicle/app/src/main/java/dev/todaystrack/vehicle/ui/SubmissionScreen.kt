package dev.todaystrack.vehicle.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.*
import android.widget.*
import dev.todaystrack.vehicle.R
import dev.todaystrack.vehicle.generation.Track
import kotlin.math.roundToInt

/** Android Views only. Each clickable frame owns a >=76dp hitbox; its smaller label is decorative. */
class SubmissionScreen(context:Context,private val actions:SubmissionActions):LinearLayout(context) {
    private val ink=Color.rgb(32,38,45);private val muted=Color.rgb(100,113,125)
    private val canvasColor=Color.rgb(244,246,248);private val accent=Color.rgb(99,117,138)
    private fun dp(n:Int)=(n*resources.displayMetrics.density).roundToInt()
    private fun bg(color:Int,radius:Int=12,stroke:Int?=null)=GradientDrawable().apply {
        setColor(color);cornerRadius=dp(radius).toFloat();stroke?.let { setStroke(dp(1),it) }
    }
    private fun label(value:String,size:Float=20f,color:Int=ink)=TextView(context).apply {
        text=value;textSize=size;setTextColor(color);includeFontPadding=false
    }
    private fun line()=View(context).apply { setBackgroundColor(Color.rgb(228,232,236));layoutParams=LayoutParams(-1,dp(1)) }
    private fun column()=LinearLayout(context).apply { orientation=VERTICAL }
    private fun space(height:Int)=View(context).apply { layoutParams=LayoutParams(1,dp(height)) }
    private class Hit(val frame:FrameLayout,val text:TextView)
    private fun hit(text:String,primary:Boolean=false,width:Int=200,action:()->Unit):Hit {
        val inner=label(text,18f,if(primary) Color.WHITE else ink).apply {
            gravity=Gravity.CENTER;setPadding(dp(if(width<=88) 4 else 12),0,dp(if(width<=88) 4 else 12),0)
            background=bg(if(primary) accent else Color.rgb(247,248,250),10)
            importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val frame=FrameLayout(context).apply {
            minimumHeight=dp(76);minimumWidth=dp(76);isClickable=true;isFocusable=true
            contentDescription=text;setOnClickListener { action() }
            addView(inner,FrameLayout.LayoutParams(dp(width),dp(52),Gravity.CENTER))
            background=StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed),bg(Color.rgb(227,232,238),12))
                addState(intArrayOf(android.R.attr.state_focused),bg(Color.rgb(227,232,238),12))
                addState(intArrayOf(),bg(Color.TRANSPARENT,12))
            }
        }
        return Hit(frame,inner)
    }
    private fun enable(hit:Hit,enabled:Boolean,text:String?=null) {
        hit.frame.isEnabled=enabled;hit.frame.alpha=if(enabled) 1f else .45f
        text?.let { hit.text.text=it;hit.frame.contentDescription=it }
    }
    private val rail=column();private val workspace=LinearLayout(context)
    private val recordsPane=column();private val recordList=column()
    private val recordScroll=ScrollView(context).apply { isFillViewport=false;addView(recordList) }
    private val panel=column();private val panelScroll=ScrollView(context).apply { isFillViewport=true;addView(panel) }
    private val recordHeading=label("최근 기록",22f).apply { typeface=Typeface.DEFAULT_BOLD }
    private val nav=linkedMapOf<ScreenPage,Hit>()
    private val navGlyphs=linkedMapOf<ScreenPage,NavGlyph>()
    private val rows=linkedMapOf<String,Pair<LinearLayout,TextView>>()
    private val songRows=linkedMapOf<String,SongRow>()
    private val header=LinearLayout(context);private val headline=label("기록을 모아보세요",28f)
    private val date=label("",18f,muted);private val distance=label("",20f)
    private val settings=label("",18f,muted);private val cover=ImageView(context)
    private val parked=label("제한 확인 중",18f,muted)
    private val status=label("",20f);private val notice=label("",18f,muted)
    private val capture=label("",18f,muted)
    private val generate=hit("음악 만들기",true) {
        val saved=if(model.intentState=="AVAILABLE") model.tracks.firstOrNull { it.sessionId==model.selectedId } else null
        if(saved!=null) actions.play(saved.id) else actions.generate()
    }
    private val detail=hit("기록 상세",width=150) { actions.details() }
    private val start=hit("새 기록 시작",width=200) { actions.start() }
    private val defaults=hit("새 기록 설정",width=150) { actions.settings() }
    private val finish=hit("기록 마무리",true) { actions.finish() }
    private val close=hit("화면 닫기",width=150) { actions.closeScreen() }
    private val choose=hit("저장된 기록 선택",width=200) { showRecords() }
    private val songs=column();private val songsTitle=label("이 기록의 음악",22f)
    private val emptySongs=label("음악을 만들면 여기에 저장됩니다",18f,muted)
    private val playerText=label("저장된 곡을 선택해 주세요",18f,muted)
    private val pause=hit("일시정지",width=120) { actions.pause() }
    private var model=SubmissionUiState();private var lastRecords:List<RecordItem>?=null
    private var lastTracks:List<Track>?=null;private var lastPage:ScreenPage?=null
    private var lastCover:String?=null;private var narrow=false
    private var savedPanelY=0;private var savedRecordsY=0
    private val art=HashMap<Int,Bitmap>()

    init {
        orientation=HORIZONTAL;setBackgroundColor(canvasColor)
        rail.setPadding(dp(6),dp(24),dp(6),dp(16));addView(rail,LayoutParams(dp(88),-1))
        rail.addView(label("오늘의\n트랙",20f).apply { gravity=Gravity.CENTER;typeface=Typeface.DEFAULT_BOLD })
        rail.addView(space(36))
        for(page in ScreenPage.entries) {
            val h=hit(page.label,width=76) { actions.page(page) };nav[page]=h
            val glyph=NavGlyph(page);navGlyphs[page]=glyph
            h.text.setCompoundDrawablesWithIntrinsicBounds(null,glyph,null,null)
            h.text.compoundDrawablePadding=dp(6)
            (h.text.layoutParams as FrameLayout.LayoutParams).height=dp(72)
            rail.addView(h.frame,LayoutParams(-1,dp(88)))
        }
        rail.addView(Space(context),LayoutParams(1,0,1f))
        rail.addView(hit("앱 정보",width=76) { actions.info() }.frame,LayoutParams(-1,dp(76)))
        workspace.orientation=HORIZONTAL;addView(workspace,LayoutParams(0,-1,1f))
        recordsPane.setPadding(dp(16),dp(28),dp(16),dp(20));workspace.addView(recordsPane,LayoutParams(dp(248),-1))
        recordsPane.addView(recordHeading)
        recordsPane.addView(space(24));recordsPane.addView(recordScroll,LayoutParams(-1,0,1f))
        val shell=column().apply { setPadding(dp(16),dp(20),dp(20),dp(20)) }
        workspace.addView(shell,LayoutParams(0,-1,1f))
        panel.setPadding(dp(28),dp(20),dp(28),dp(24));panel.background=bg(Color.WHITE,24)
        shell.addView(panelScroll,LayoutParams(-1,-1))
        panel.addView(parked.apply { gravity=Gravity.END })
        panel.addView(choose.frame,LayoutParams(dp(224),dp(76)))
        header.orientation=HORIZONTAL;header.gravity=Gravity.CENTER_VERTICAL
        cover.scaleType=ImageView.ScaleType.CENTER_CROP;cover.background=bg(Color.LTGRAY,16)
        cover.clipToOutline=true;cover.importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
        header.addView(cover,LayoutParams(dp(200),dp(200)).apply { marginEnd=dp(28) })
        headline.typeface=Typeface.DEFAULT_BOLD;songsTitle.typeface=Typeface.DEFAULT_BOLD
        val copy=column();copy.addView(headline);copy.addView(space(12));copy.addView(date)
        copy.addView(space(20));copy.addView(distance);copy.addView(space(12));copy.addView(settings)
        header.addView(copy,LayoutParams(0,-2,1f));panel.addView(header)
        panel.addView(space(20));panel.addView(status);panel.addView(space(8));panel.addView(notice)
        val actionRow=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        actionRow.addView(generate.frame,LayoutParams(dp(224),dp(76)))
        actionRow.addView(detail.frame,LayoutParams(dp(166),dp(76)));panel.addView(actionRow)
        val recordingRow=LinearLayout(context)
        recordingRow.addView(start.frame,LayoutParams(dp(224),dp(76)))
        recordingRow.addView(defaults.frame,LayoutParams(dp(166),dp(76)));panel.addView(recordingRow)
        panel.addView(capture)
        val activeRow=LinearLayout(context)
        activeRow.addView(finish.frame,LayoutParams(dp(224),dp(76)))
        activeRow.addView(close.frame,LayoutParams(dp(166),dp(76)));panel.addView(activeRow)
        panel.addView(space(20));panel.addView(line());panel.addView(space(20));panel.addView(songsTitle)
        panel.addView(space(12));panel.addView(songs);songs.addView(emptySongs)
        val playback=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        playback.addView(playerText,LayoutParams(0,-2,1f));playback.addView(pause.frame,LayoutParams(dp(136),dp(76)))
        panel.addView(playback)
        panel.addView(label("정밀 위치는 차량에 보관됩니다 · 표지는 고정 기본 이미지입니다",16f,muted))
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        val next=w/resources.displayMetrics.density<960
        if(next!=narrow) { narrow=next;render(model) }
    }
    private fun image(view:ImageView,id:String) {
        val res=intArrayOf(R.drawable.cover_concrete,R.drawable.cover_curve,R.drawable.cover_planes)[Math.floorMod(id.hashCode(),3)]
        val bitmap=art.getOrPut(res) { BitmapFactory.decodeResource(resources,res,BitmapFactory.Options().apply { inSampleSize=2 }) }
        view.setImageBitmap(bitmap)
    }
    fun render(next:SubmissionUiState) {
        model=next
        parked.text=if(next.controls.allowed) "정차 중" else "조작 제한 · 상태 확인 필요"
        for((page,h) in nav) {
            h.frame.isSelected=page==next.page
            navGlyphs[page]?.color=if(page==next.page) accent else ink
            h.text.setTextColor(if(page==next.page) accent else ink)
            h.text.typeface=if(page==next.page) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            h.text.background=bg(if(page==next.page) Color.rgb(226,232,239) else Color.TRANSPARENT,10)
        }
        recordsPane.visibility=if(narrow || next.page==ScreenPage.LIBRARY) GONE else VISIBLE
        recordHeading.text=if(next.page==ScreenPage.RECORDS) "모든 기록" else "최근 기록"
        choose.frame.visibility=if(narrow && next.page!=ScreenPage.LIBRARY) VISIBLE else GONE
        enable(choose,next.controls.canSelect)
        val visible=if(next.page==ScreenPage.HOME) next.records.filter { it.open }+next.records.filterNot { it.open }.take(5) else next.records
        if(visible!=lastRecords) { syncRecords(visible);lastRecords=visible }
        for((id,pair) in rows) {
            pair.first.isSelected=id==next.selectedId
            pair.first.isEnabled=next.controls.canSelect
            pair.first.background=bg(if(id==next.selectedId) Color.rgb(229,234,240) else Color.TRANSPARENT,14)
            pair.first.alpha=if(next.controls.canSelect) 1f else .55f
        }
        val library=next.page==ScreenPage.LIBRARY
        header.visibility=if(library) GONE else VISIBLE
        val record=next.records.firstOrNull { it.id==next.selectedId }
        headline.text=record?.title ?: "기록을 모아보세요"
        date.text=record?.date ?: "사진·메모 없이 시작할 수 있어요"
        distance.text=next.distance;settings.text=record?.settings ?: next.defaults
        val coverId=record?.id ?: "default"
        if(lastCover!=coverId) { image(cover,coverId);lastCover=coverId }
        status.visibility=if(library) GONE else VISIBLE
        status.text=next.message ?: ((if(next.fixture) "대역 · 새 AI 생성 아님\n" else "")+next.status)
        notice.visibility=if(library) GONE else VISIBLE
        notice.text="음악 만들기를 누르면 정확한 위치를 뺀 요약을 생성 서버·음악 AI로 전송합니다."
        generate.frame.visibility=if(library || record==null || record.open) GONE else VISIBLE
        detail.frame.visibility=if(library || record==null) GONE else VISIBLE
        val canListen=next.intentState=="AVAILABLE" && next.tracks.any { it.sessionId==next.selectedId }
        enable(generate,if(canListen) next.controls.allowed else next.controls.canGenerate,
            if(canListen) "음악 듣기" else if(next.intentState==null) "음악 만들기" else if(next.intentState=="AVAILABLE") "저장 완료" else "기존 작업 확인")
        enable(start,next.controls.canStart,if(next.controls.hasOpen) "이전 기록 이어서 수집" else "새 기록 시작")
        enable(defaults,next.controls.canChangeSettings)
        start.frame.visibility=if(library || next.capturing) GONE else VISIBLE
        defaults.frame.visibility=if(library || next.capturing) GONE else VISIBLE
        finish.frame.visibility=if(next.capturing) VISIBLE else GONE;enable(finish,next.controls.canStop)
        close.frame.visibility=if(next.capturing) VISIBLE else GONE
        capture.visibility=if(next.capturing) VISIBLE else GONE;capture.text=next.captureStatus
        songsTitle.text=if(library) "나의 보관함" else "이 기록의 음악"
        val tracks=if(library) next.tracks else next.tracks.filter { it.sessionId==next.selectedId }
        if(tracks!=lastTracks || library!=(lastPage==ScreenPage.LIBRARY)) { syncSongs(tracks);lastTracks=tracks }
        songRows.values.forEach { it.play.frame.isEnabled=next.controls.allowed;it.play.frame.alpha=if(next.controls.allowed) 1f else .45f }
        if(lastPage!=next.page) { panelScroll.scrollTo(0,0);lastPage=next.page }
    }
    private fun syncRecords(items:List<RecordItem>) {
        val ids=items.map { it.id }.toSet()
        for(id in rows.keys.toList()) if(id !in ids) { recordList.removeView(rows.remove(id)!!.first) }
        for((index,item) in items.withIndex()) {
            val existing=rows[item.id]
            val row=existing?.first ?: column().apply {
                minimumHeight=dp(100);isClickable=true;isFocusable=true;setPadding(dp(16),dp(18),dp(12),dp(18))
                val t=label("").apply { setLineSpacing(dp(6).toFloat(),1f);importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO };addView(t)
                setOnClickListener { actions.select(item.id) };rows[item.id]=this to t
            }
            val text=item.title+"\n"+item.date+(if(item.open) " · 진행 중" else "")
            if(rows[item.id]!!.second.text.toString()!=text) rows[item.id]!!.second.text=text
            row.contentDescription="기록 선택 · $text"
            if(recordList.indexOfChild(row)!=index) { if(row.parent!=null) recordList.removeView(row);recordList.addView(row,index) }
        }
    }
    private data class SongRow(val root:LinearLayout,val title:TextView,val meta:TextView,val play:Hit)
    private fun syncSongs(items:List<Track>) {
        emptySongs.visibility=if(items.isEmpty()) VISIBLE else GONE
        val ids=items.map { it.id }.toSet()
        for(id in songRows.keys.toList()) if(id !in ids) songs.removeView(songRows.remove(id)!!.root)
        for((index,track) in items.withIndex()) {
            val row=songRows[track.id] ?: run {
                val root=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(8),0,dp(8));minimumHeight=dp(96) }
                val thumb=ImageView(context).apply { scaleType=ImageView.ScaleType.CENTER_CROP;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
                image(thumb,track.sessionId);root.addView(thumb,LayoutParams(dp(68),dp(68)).apply { marginEnd=dp(16) })
                val copy=column();val t=label(track.title);val m=label("",17f,muted)
                copy.addView(t);copy.addView(space(6));copy.addView(m);root.addView(copy,LayoutParams(0,-2,1f))
                val play=hit("재생",width=76) { actions.play(track.id) };root.addView(play.frame,LayoutParams(dp(76),dp(76)))
                SongRow(root,t,m,play).also { songRows[track.id]=it }
            }
            row.title.text=track.title
            row.meta.text="${track.durationMs/60000}:${"%02d".format(track.durationMs/1000%60)} · "+if(track.provenance=="FIXTURE") "대역 · 새 생성 아님" else "새 AI 음악"
            val recordDate=model.records.firstOrNull { it.id==track.sessionId }?.date.orEmpty()
            row.meta.text=row.meta.text.toString()+if(recordDate.isEmpty()) "" else " · $recordDate"
            row.play.frame.contentDescription="재생 · ${track.title} · $recordDate"
            if(songs.indexOfChild(row.root)!=index+1) { if(row.root.parent!=null) songs.removeView(row.root);songs.addView(row.root,index+1) }
        }
    }
    fun playback(state:PlaybackUiState) { playerText.text=state.label;enable(pause,state.playing) }
    private fun showRecords() {
        val items=if(model.page==ScreenPage.HOME) model.records.filter { it.open }+model.records.filterNot { it.open }.take(5) else model.records
        if(!model.controls.canSelect || items.isEmpty()) return
        val list=column()
        val dialog=android.app.AlertDialog.Builder(context).setTitle("저장된 기록 선택").setView(ScrollView(context).apply { addView(list) }).create()
        items.forEach { item -> val h=hit(item.title+" · "+item.date,width=360) { if(model.controls.canSelect) actions.select(item.id);dialog.dismiss() };list.addView(h.frame,LayoutParams(-1,dp(76))) }
        dialog.show()
    }
    fun saveScroll(out:android.os.Bundle) { out.putInt("panel_scroll",panelScroll.scrollY);out.putInt("records_scroll",recordScroll.scrollY) }
    fun restoreScroll(state:android.os.Bundle?) { savedPanelY=state?.getInt("panel_scroll") ?: 0;savedRecordsY=state?.getInt("records_scroll") ?: 0 }
    fun restoreAfterFirstRender() { post { panelScroll.scrollTo(0,savedPanelY);recordScroll.scrollTo(0,savedRecordsY) } }

    private inner class NavGlyph(private val page:ScreenPage):Drawable() {
        var color:Int=ink
            set(value) { field=value;invalidateSelf() }
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE;strokeWidth=1.7f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND }
        override fun getIntrinsicWidth()=dp(24)
        override fun getIntrinsicHeight()=dp(24)
        override fun draw(canvas:Canvas) {
            canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/24f,bounds.height()/24f);paint.color=color
            when(page) {
                ScreenPage.HOME -> { val p=Path();p.moveTo(3f,10f);p.lineTo(12f,3f);p.lineTo(21f,10f);p.lineTo(21f,21f);p.lineTo(15f,21f);p.lineTo(15f,14f);p.lineTo(9f,14f);p.lineTo(9f,21f);p.lineTo(3f,21f);p.close();canvas.drawPath(p,paint) }
                ScreenPage.RECORDS -> { canvas.drawRoundRect(4f,3f,20f,21f,2f,2f,paint);for(y in listOf(8f,12f,16f)) canvas.drawLine(8f,y,16f,y,paint) }
                ScreenPage.LIBRARY -> { canvas.drawLine(8f,18f,8f,5f,paint);canvas.drawLine(8f,5f,20f,3f,paint);canvas.drawLine(20f,3f,20f,16f,paint);canvas.drawOval(3f,16f,8f,21f,paint);canvas.drawOval(15f,14f,20f,19f,paint) }
            }
            canvas.restore()
        }
        override fun setAlpha(alpha:Int) { paint.alpha=alpha }
        override fun setColorFilter(filter:ColorFilter?) { paint.colorFilter=filter }
        @Deprecated("Drawable compatibility") override fun getOpacity()=PixelFormat.TRANSLUCENT
    }
}
