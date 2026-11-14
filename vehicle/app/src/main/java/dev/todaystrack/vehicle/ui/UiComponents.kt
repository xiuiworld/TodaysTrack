package dev.todaystrack.vehicle.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import dev.todaystrack.vehicle.R
import dev.todaystrack.vehicle.generation.Track
import kotlin.math.roundToInt

internal object UiColors {
    val ink=Color.rgb(36,40,47);val muted=Color.rgb(89,97,107)
    val border=Color.rgb(226,229,233);val player=Color.rgb(38,63,101)
    val accent=Color.rgb(37,42,50);val selected=Color.rgb(245,245,246)
}
/** Fit the approved 841 x 605 app canvas to the available window, not the display density. */
internal object UiSizing {
    fun scale(widthDp:Float,heightDp:Float)=minOf(widthDp/841f,heightDp/605f).coerceAtLeast(1f)
}
internal class UiKit(val context:Context,var scale:Float=1f) {
    fun dp(n:Float)=(n*context.resources.displayMetrics.density).roundToInt()
    fun dp(n:Int)=dp(n.toFloat())
    fun unit(n:Int)=dp(n*scale)
    val font get()=20f*scale
    val hit get()=unit(76)
    fun background(color:Int,radius:Int=10,stroke:Int?=null)=GradientDrawable().apply {
        setColor(color);cornerRadius=dp(radius).toFloat();stroke?.let { setStroke(dp(1),it) }
    }
    fun startRule()=object:Drawable() {
        private val pen=Paint().apply { color=UiColors.border;strokeWidth=dp(1).toFloat() }
        override fun draw(canvas:Canvas) { val x=if(context.resources.configuration.layoutDirection==View.LAYOUT_DIRECTION_RTL) bounds.right.toFloat() else bounds.left.toFloat();canvas.drawLine(x,bounds.top.toFloat(),x,bounds.bottom.toFloat(),pen) }
        override fun setAlpha(alpha:Int) { pen.alpha=alpha }
        override fun setColorFilter(filter:ColorFilter?) { pen.colorFilter=filter }
        @Deprecated("Drawable compatibility") override fun getOpacity()=PixelFormat.TRANSLUCENT
    }
    fun column()=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
    fun row()=LinearLayout(context).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
    fun space(n:Int)=Space(context).apply { layoutParams=LinearLayout.LayoutParams(unit(n),unit(n)) }
    fun line()=View(context).apply { setBackgroundColor(UiColors.border);layoutParams=LinearLayout.LayoutParams(-1,dp(1)) }
    fun text(value:String,size:Float=font-4,bold:Boolean=false)=TextView(context).apply {
        text=value;textSize=size;setTextColor(if(bold) UiColors.ink else UiColors.muted)
        includeFontPadding=false;minimumHeight=dp(size*1.45f);setLineHeight(dp(size*1.45f))
        if(bold) typeface=Typeface.DEFAULT_BOLD
    }
    fun heading(value:String,small:Boolean=false)=text(value,if(small) font else font*1.4f,true).apply { isAccessibilityHeading=true;minimumHeight=dp((if(small) font else font*1.4f)*1.3f);setLineHeight(minimumHeight) }
    fun icon(name:String,color:Int=UiColors.ink)=ImageView(context).apply {
        setImageDrawable(UiGlyph(context,name,color));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams=LinearLayout.LayoutParams(unit(24),unit(24))
    }
    fun cover(id:String,size:Int)=ImageView(context).apply {
        val resources=intArrayOf(R.drawable.cover_concrete,R.drawable.cover_curve,R.drawable.cover_planes)
        setImageResource(resources[Math.floorMod(id.hashCode(),3)])
        scaleType=ImageView.ScaleType.CENTER_CROP;background=background(Color.WHITE);clipToOutline=true
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams=LinearLayout.LayoutParams(unit(size),unit(size))
    }
    fun placeholder(size:Int,name:String)=FrameLayout(context).apply {
        background=background(Color.WHITE,10,UiColors.border)
        addView(icon(name,UiColors.muted),FrameLayout.LayoutParams(unit(24),unit(24),Gravity.CENTER))
        layoutParams=LinearLayout.LayoutParams(unit(size),unit(size))
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    fun action(label:String,width:Int=200,primary:Boolean=false,quiet:Boolean=false,icon:String?=null,onClick:()->Unit):FrameLayout {
        val frame=ButtonFrame(context).apply {
            minimumWidth=hit;minimumHeight=hit;isFocusable=true;isClickable=true
            contentDescription=label;setOnClickListener { if(isEnabled) onClick() }
            layoutParams=LinearLayout.LayoutParams(unit(width),hit)
        }
        val face=FrameLayout(context).apply {
            tag="action-face";isDuplicateParentStateEnabled=true;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            val base=if(primary) UiColors.accent else Color.WHITE
            background=StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed),background(if(primary) Color.rgb(29,33,40) else UiColors.selected,10))
                addState(intArrayOf(android.R.attr.state_focused),background(base,10,UiColors.player))
                addState(intArrayOf(),background(if(quiet) Color.TRANSPARENT else base,10,if(primary||quiet) null else UiColors.border))
            }
            val child=if(icon!=null) icon(icon,if(primary) Color.WHITE else UiColors.ink) else text(label,font-2,true).apply {
                gravity=Gravity.CENTER;setTextColor(if(primary) Color.WHITE else UiColors.ink);setPadding(dp(8),0,dp(8),0)
            }
            addView(child,FrameLayout.LayoutParams(if(icon!=null) unit(24) else -1,if(icon!=null) unit(24) else -1,Gravity.CENTER))
        }
        frame.addView(face,FrameLayout.LayoutParams(unit(width-24),unit(48),Gravity.CENTER));return frame
    }
    fun enable(view:View,enabled:Boolean) { view.isEnabled=enabled;view.alpha=if(enabled) 1f else .45f }
}
internal open class ButtonFrame(context:Context):FrameLayout(context) {
    override fun getAccessibilityClassName():CharSequence="android.widget.Button"
}
internal class PlayToggle(private val kit:UiKit,val track:Track,private val actions:SubmissionActions):ButtonFrame(kit.context) {
    private val disc=FrameLayout(context).apply {
        tag="play-disc";isDuplicateParentStateEnabled=true;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        background=StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed),kit.background(Color.rgb(30,50,80),50))
            addState(intArrayOf(android.R.attr.state_focused),kit.background(UiColors.player,50,UiColors.ink))
            addState(intArrayOf(),kit.background(UiColors.player,50))
        }
    }
    private val glyph=ImageView(context)
    private var playing=false
    init {
        minimumWidth=kit.hit;minimumHeight=kit.hit;isClickable=true;isFocusable=true
        layoutParams=LinearLayout.LayoutParams(kit.hit,kit.hit)
        disc.addView(glyph,LayoutParams(kit.unit(24),kit.unit(24),Gravity.CENTER))
        addView(disc,LayoutParams(kit.unit(44),kit.unit(44),Gravity.CENTER))
        setOnClickListener { if(isEnabled) { if(playing) actions.pause() else actions.play(track.id) } }
    }
    fun bind(state:SubmissionUiState,playback:PlaybackUiState) {
        playing=playback.trackId==track.id&&playback.playing
        contentDescription=(if(playing) "일시정지" else "재생")+" · "+track.title
        glyph.setImageDrawable(UiGlyph(context,if(playing) "pause" else "play",Color.WHITE))
        kit.enable(this,playing||state.controls.allowed)
    }
}
/** Read-only MediaController projection. Does not invent elapsed time or dispatch seek/play. */
internal class PlaybackStrip(private val kit:UiKit,val track:Track,actions:SubmissionActions):LinearLayout(kit.context) {
    val toggle=PlayToggle(kit,track,actions)
    private val progress=PositionBar(kit)
    private val elapsed=kit.text("0:00");private val total=kit.text(UiPresentation.clock(track.durationMs.takeIf { it>0 }))
    init {
        orientation=VERTICAL;minimumHeight=kit.hit;contentDescription="재생 제어"
        val row=kit.row();row.addView(toggle);row.addView(kit.space(12))
        val frame=FrameLayout(context)
        frame.addView(progress,FrameLayout.LayoutParams(-1,kit.hit))
        val times=kit.row().apply { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
        times.addView(elapsed,LayoutParams(0,-2,1f));times.addView(total)
        frame.addView(times,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
        row.addView(frame,LayoutParams(0,kit.hit,1f));addView(row,LayoutParams(-1,kit.hit))
    }
    fun bind(state:SubmissionUiState,playback:PlaybackUiState) {
        toggle.bind(state,playback)
        val current=playback.trackId==track.id
        val duration=(if(current) playback.durationMs else null)?.takeIf { it>0 } ?: track.durationMs.takeIf { it>0 }
        val position=if(current) playback.positionMs.coerceAtLeast(0) else 0
        elapsed.text=UiPresentation.clock(if(duration!=null) position.coerceAtMost(duration) else position)
        total.text=UiPresentation.clock(duration)
        progress.fraction=if(duration==null) 0f else position.toFloat().coerceIn(0f,duration.toFloat())/duration
        progress.contentDescription="재생 위치 · ${elapsed.text} / ${total.text} · 위치 표시"
    }
}
private class PositionBar(private val kit:UiKit):View(kit.context) {
    var fraction=0f;set(value) { field=value;invalidate() }
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas);val radius=kit.unit(4).toFloat();val y=kit.hit/2f
        val end=width-radius;val start=radius
        paint.color=UiColors.border;canvas.drawRoundRect(start,y-kit.dp(3),end,y+kit.dp(3),radius,radius,paint)
        val position=start+(end-start)*fraction
        paint.color=UiColors.player;canvas.drawRoundRect(start,y-kit.dp(3),position,y+kit.dp(3),radius,radius,paint)
        canvas.drawCircle(position,y,kit.unit(6).toFloat(),paint)
    }
}
internal class UiGlyph(context:Context,private val name:String,private val tint:Int):Drawable() {
    private val size=(24*context.resources.displayMetrics.density).roundToInt()
    private val pen=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE;strokeWidth=1.7f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND;color=tint }
    override fun getIntrinsicWidth()=size
    override fun getIntrinsicHeight()=size
    override fun draw(canvas:Canvas) {
        canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/24f,bounds.height()/24f)
        val p=Path()
        when(name) {
            "home" -> { p.moveTo(3f,10f);p.lineTo(12f,3f);p.lineTo(21f,10f);p.lineTo(21f,21f);p.lineTo(15f,21f);p.lineTo(15f,14f);p.lineTo(9f,14f);p.lineTo(9f,21f);p.lineTo(3f,21f);p.close();canvas.drawPath(p,pen) }
            "records" -> { canvas.drawRect(5f,3f,19f,21f,pen);for(y in listOf(8f,12f,16f))canvas.drawLine(8f,y,16f,y,pen) }
            "library" -> { canvas.drawLine(8f,18f,8f,5f,pen);canvas.drawLine(8f,5f,20f,3f,pen);canvas.drawLine(20f,3f,20f,16f,pen);canvas.drawOval(2f,15f,8f,21f,pen);canvas.drawOval(14f,13f,20f,19f,pen) }
            "play" -> { p.moveTo(9f,5f);p.lineTo(20f,12f);p.lineTo(9f,19f);p.close();canvas.drawPath(p,pen) }
            "pause" -> { canvas.drawLine(8f,5f,8f,19f,pen);canvas.drawLine(16f,5f,16f,19f,pen) }
            "back" -> { p.moveTo(12f,5f);p.lineTo(5f,12f);p.lineTo(12f,19f);canvas.drawPath(p,pen);canvas.drawLine(5f,12f,20f,12f,pen) }
            "chevron" -> { p.moveTo(9f,5f);p.lineTo(16f,12f);p.lineTo(9f,19f);canvas.drawPath(p,pen) }
            "close" -> { canvas.drawLine(6f,6f,18f,18f,pen);canvas.drawLine(18f,6f,6f,18f,pen) }
            "more" -> { for(x in listOf(5f,12f,19f))canvas.drawCircle(x,12f,1f,pen) }
            "info" -> { canvas.drawCircle(12f,12f,9f,pen);canvas.drawPoint(12f,7f,pen);canvas.drawLine(12f,11f,12f,17f,pen) }
            "settings" -> { for(i in 0..7) { canvas.save();canvas.rotate(i*45f,12f,12f);canvas.drawLine(12f,2f,12f,5f,pen);canvas.restore() };canvas.drawCircle(12f,12f,7f,pen);canvas.drawCircle(12f,12f,3f,pen) }
        }
        canvas.restore()
    }
    override fun setAlpha(alpha:Int) { pen.alpha=alpha }
    override fun setColorFilter(colorFilter:ColorFilter?) { pen.colorFilter=colorFilter }
    @Deprecated("Drawable compatibility") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
