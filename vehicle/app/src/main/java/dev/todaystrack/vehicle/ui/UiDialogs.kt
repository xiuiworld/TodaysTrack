package dev.todaystrack.vehicle.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** Shared display shell. Preference values and effects stay in MainActivity. */
object UiDialogs {
    private fun kit(context:Context)=UiKit(context,if(context.resources.configuration.screenWidthDp>=1400) 1.5f else 1f)
    fun body(context:Context)=kit(context).column()
    fun text(context:Context,value:String)=kit(context).text(value)
    fun disclosure(context:Context,title:String,value:String):View {
        val k=kit(context);val root=k.column();val detail=k.text(value).apply { visibility=View.GONE }
        val toggle=k.action(title,quiet=true,width=360) {
            detail.visibility=if(detail.visibility==View.GONE) View.VISIBLE else View.GONE
        }
        root.addView(toggle);root.addView(detail);return root
    }
    fun row(context:Context,label:String,value:String):View {
        val k=kit(context);return k.column().apply {
            val row=k.row().apply { setPadding(0,k.dp(12),0,k.dp(12)) }
            row.addView(k.text(label),LinearLayout.LayoutParams(k.unit(128),-2))
            row.addView(k.text(value,k.font-2,true),LinearLayout.LayoutParams(0,-2,1f))
            addView(row);addView(k.line())
        }
    }
    fun choice(context:Context,title:String,labels:Array<String>,selection:Int)=ChoiceField(kit(context),title,labels,selection)
    fun show(context:Context,title:String,body:View,confirm:String="확인",cancel:Boolean=false,extra:Pair<String,()->Unit>?=null,onConfirm:()->Unit={}):AlertDialog {
        val k=kit(context);val root=k.column();val dialog=AlertDialog.Builder(context).setView(root).create()
        val header=k.row().apply { minimumHeight=k.hit;setPadding(k.dp(20),0,k.dp(8),0) }
        header.addView(k.heading(title),LinearLayout.LayoutParams(0,-2,1f))
        header.addView(k.action("닫기",76,quiet=true,icon="close") { dialog.dismiss() });root.addView(header);root.addView(k.line())
        val scroll=object:ScrollView(context) {
            override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
                val limit=(resources.displayMetrics.heightPixels-k.hit*2-k.dp(80)).coerceAtLeast(k.hit)
                super.onMeasure(widthMeasureSpec,MeasureSpec.makeMeasureSpec(limit,MeasureSpec.AT_MOST))
            }
        }.apply { setPadding(k.dp(20),k.dp(12),k.dp(20),k.dp(12));clipToPadding=false;addView(body) }
        root.addView(scroll,LinearLayout.LayoutParams(-1,-2));root.addView(k.line())
        val footer=k.row().apply { gravity=Gravity.END or Gravity.CENTER_VERTICAL;setPadding(k.dp(12),k.dp(4),k.dp(20),k.dp(4)) }
        extra?.let { (label,action)->footer.addView(k.action(label,176,quiet=true) { dialog.dismiss();action() }) }
        if(cancel)footer.addView(k.action("취소",132,quiet=true) { dialog.dismiss() })
        footer.addView(k.action(confirm,132,primary=true) { dialog.dismiss();onConfirm() });root.addView(footer)
        root.background=k.background(Color.WHITE,20)
        dialog.show();dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent);setDimAmount(.24f)
            setLayout(minOf(k.unit(720),context.resources.displayMetrics.widthPixels-k.dp(64)),ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return dialog
    }
    class ChoiceField internal constructor(private val kit:UiKit,title:String,labels:Array<String>,selection:Int):LinearLayout(kit.context) {
        var selectedItemPosition:Int=selection.coerceIn(0,labels.lastIndex);private set
        private val choices=mutableListOf<FrameLayout>()
        init {
            orientation=VERTICAL;addView(kit.text(title,kit.font-2,true));addView(kit.space(4))
            val columns=if(labels.size>3) 3 else labels.size
            for(start in labels.indices step columns) {
                val row=kit.row()
                for(index in start until minOf(start+columns,labels.size)) {
                    val option=kit.action(labels[index],if(columns==2) 260 else 176) { selectedItemPosition=index;refresh() }
                    option.contentDescription="$title · ${labels[index]}";choices+=option
                    row.addView(option,LayoutParams(0,kit.hit,1f))
                    option.getChildAt(0).layoutParams=FrameLayout.LayoutParams(-1,kit.unit(48),Gravity.CENTER).apply { marginStart=kit.dp(4);marginEnd=kit.dp(4) }
                }
                repeat(columns-(minOf(start+columns,labels.size)-start)) { row.addView(Space(context),LayoutParams(0,kit.hit,1f)) }
                addView(row,LayoutParams(-1,-2))
            }
            refresh()
        }
        private fun refresh() {
            choices.forEachIndexed { index,button->
                button.isSelected=index==selectedItemPosition
                button.getChildAt(0).background=kit.background(if(button.isSelected) UiColors.accent else Color.WHITE,10,if(button.isSelected) null else UiColors.border)
                val face=button.getChildAt(0) as ViewGroup
                (face.getChildAt(0) as TextView).setTextColor(if(button.isSelected) Color.WHITE else UiColors.ink)
            }
        }
    }
}
