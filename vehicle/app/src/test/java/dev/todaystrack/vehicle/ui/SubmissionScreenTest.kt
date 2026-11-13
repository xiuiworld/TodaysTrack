package dev.todaystrack.vehicle.ui

import android.app.Application
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dev.todaystrack.vehicle.generation.Track
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.annotation.Config

/** Host Views only: never constructs MainActivity, the real Application, Car, Room or Media3. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class, qualifiers="w841dp-h605dp-mdpi")
class SubmissionScreenTest {
    private class Events:SubmissionActions {
        val calls=mutableListOf<String>()
        override fun page(page:ScreenPage) { calls+="page:$page" }
        override fun select(id:String) { calls+="select:$id" }
        override fun start() { calls+="start" }
        override fun finish() { calls+="finish" }
        override fun closeScreen() { calls+="close" }
        override fun settings() { calls+="settings" }
        override fun generate() { calls+="generate" }
        override fun details() { calls+="details" }
        override fun play(id:String) { calls+="play:$id" }
        override fun pause() { calls+="pause" }
        override fun info() { calls+="info" }
    }
    private val record=RecordItem("actual-record","여행 기록","11월 12일 · 16:08:00","어쿠스틱 · 무가사",false)
    private val track=Track("actual-track","actual-job",record.id,"frozen-input","/not-opened.mp3","sha",17,"LOCAL_TEST","여행 기록의 음악",123000)
    private fun state(page:ScreenPage=ScreenPage.HOME)=SubmissionUiState(page=page,records=listOf(record),selectedId=record.id,
        distance="연속 관측 거리 6.00 km",status="곡이 저장되었습니다",intentState="AVAILABLE",fixture=true,
        controls=UiControls(true,true,false,false,false,true,true,"AVAILABLE"),tracks=listOf(track),defaults="하루 기록 · 잔잔한 팝 · 무가사")
    private fun tree(view:View):List<View> = listOf(view)+(if(view is ViewGroup) (0 until view.childCount).flatMap { tree(view.getChildAt(it)) } else emptyList())
    private fun named(view:View,name:String)=tree(view).single { it.contentDescription?.toString()==name && it.visibility==View.VISIBLE }
    private fun texts(view:View)=tree(view).filterIsInstance<TextView>().filter { it.visibility==View.VISIBLE }.map { it.text.toString() }
    private fun origin(view:View):IntArray {
        val result=intArrayOf(view.left,view.top);var parent=view.parent
        while(parent is View) { result[0]+=parent.left-parent.scrollX;result[1]+=parent.top-parent.scrollY;parent=parent.parent }
        return result
    }
    private fun layout(view:View,w:Int=841,h:Int=605) { view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h) }

    @Test fun approvedHomeHasHeaderClusterBottomNavigationAndSmallPlaybackCircle() {
        val events=Events();val screen=SubmissionScreen(RuntimeEnvironment.getApplication(),events)
        screen.render(state());layout(screen)
        assertTrue(texts(screen).contains("음악 저장 완료"))
        assertFalse(texts(screen).contains("최근 기록"))
        val info=named(screen,"앱 정보");val settings=named(screen,"새 기록 설정")
        val infoPos=origin(info);val settingsPos=origin(settings)
        assertTrue(settingsPos[0]>infoPos[0]);assertEquals(infoPos[1],settingsPos[1])
        named(screen,"보관함으로").performClick();assertEquals("page:LIBRARY",events.calls.last())
        val play=named(screen,"재생 · ${track.title}")
        assertTrue(play.width>=76);assertTrue(play.height>=76)
        assertTrue(tree(play).any { it.tag=="play-disc"&&it.width==44&&it.height==44 })
    }

    @Test fun libraryOpensItsOwnDetailAndKeepsOriginalRecordLink() {
        val events=Events();val screen=SubmissionScreen(RuntimeEnvironment.getApplication(),events)
        screen.render(state(ScreenPage.LIBRARY));layout(screen)
        named(screen,"음악 상세 · ${track.title} · ${record.date}").performClick();layout(screen)
        assertTrue(texts(screen).contains("음악 상세"))
        assertFalse(events.calls.any { it.startsWith("select:") })
        val cover=tree(screen).single { it.tag=="album-cover-large" };assertEquals(240,cover.width)
        assertTrue(texts(screen).contains("대역 · 새 AI 생성 아님"))
        named(screen,"원래 기록 보기").performClick()
        assertTrue(events.calls.contains("page:RECORDS"))
    }

    @Test fun recordRowsOpenPageWithBackArrowAndRelatedSong() {
        val events=Events();val screen=SubmissionScreen(RuntimeEnvironment.getApplication(),events)
        screen.render(state(ScreenPage.RECORDS));layout(screen)
        named(screen,"기록 상세 · ${record.title} · ${record.date}").performClick();layout(screen)
        assertTrue(texts(screen).contains("이 기록의 음악"));assertTrue(texts(screen).contains(track.title))
        named(screen,"기록 목록으로 돌아가기").performClick();layout(screen)
        assertTrue(texts(screen).contains("기록"));assertFalse(texts(screen).contains("이 기록의 음악"))
    }

    @Test fun restrictionsKeepPauseAvailableAndNeverDispatchNewPlay() {
        val events=Events();val screen=SubmissionScreen(RuntimeEnvironment.getApplication(),events)
        val restricted=state().copy(controls=state().controls.copy(allowed=false))
        screen.render(restricted);screen.playback(PlaybackUiState(track.id,"재생 중",true));layout(screen)
        val pause=named(screen,"일시정지 · ${track.title}");assertTrue(pause.isEnabled);pause.performClick()
        assertEquals(listOf("pause"),events.calls)
        screen.playback(PlaybackUiState(track.id,"일시정지",false));layout(screen)
        assertFalse(named(screen,"재생 · ${track.title}").isEnabled)
    }

    @Test fun recordsAndLibraryShareTitleCoordinatesAtBothWindowSizes() {
        for((width,height) in listOf(841 to 605,1682 to 1210)) {
            val screen=SubmissionScreen(RuntimeEnvironment.getApplication(),Events())
            fun titleAt(page:ScreenPage):IntArray {
                screen.render(state(page));layout(screen,width,height)
                return origin(tree(screen).single { it.tag=="collection-title" })
            }
            assertArrayEquals(titleAt(ScreenPage.RECORDS),titleAt(ScreenPage.LIBRARY))
        }
    }

    @Test fun clockUsesActualTrackAndPlayerValuesWithoutPrototypeData() {
        val screen=SubmissionScreen(RuntimeEnvironment.getApplication(),Events())
        screen.render(state());screen.playback(PlaybackUiState(track.id,"재생 중",true,45000,123000));layout(screen)
        assertTrue(texts(screen).contains("0:45"));assertTrue(texts(screen).contains("2:03"))
        assertFalse(texts(screen).contains("1:30"));assertFalse(texts(screen).any { "예시" in it })
        assertFalse(tree(screen).any { it is android.widget.SeekBar })
    }

    @Test fun recordingAndGenerationConfirmationDispatchExistingCallbacksOnly() {
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val events=Events();val screen=SubmissionScreen(controller.get(),events)
            controller.get().setContentView(screen)
            val sealed=state().copy(intentState=null,tracks=emptyList(),controls=state().controls.copy(intentState=null))
            val capturing=sealed.copy(capturing=true,records=listOf(record.copy(open=true)),controls=sealed.controls.copy(capturing=true,hasOpen=true,sealed=false))
            screen.render(capturing);layout(screen);named(screen,"기록 마감").performClick()
            assertEquals(listOf("finish"),events.calls)
            screen.render(sealed);layout(screen);named(screen,"음악 만들기").performClick()
            var dialog=ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(dialog.isShowing);assertTrue(texts(dialog.window!!.decorView).any { "정밀 위치를 뺀 기록 요약" in it })
            named(dialog.window!!.decorView,"취소").performClick();assertEquals(listOf("finish"),events.calls)
            named(screen,"음악 만들기").performClick();dialog=ShadowAlertDialog.getLatestAlertDialog()
            named(dialog.window!!.decorView,"만들기").performClick();assertEquals(listOf("finish","generate"),events.calls)
            named(screen,"음악 만들기").performClick();dialog=ShadowAlertDialog.getLatestAlertDialog()
            screen.render(sealed.copy(controls=sealed.controls.copy(allowed=false)))
            assertFalse(dialog.isShowing);assertEquals(listOf("finish","generate"),events.calls)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun settingsKeepAllTenChoicesAndCancelDoesNotSave() {
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val context=controller.get();val labels=arrayOf("자동","팝","어쿠스틱","일렉트로닉","펑크","드림 팝","록","R&B","재즈","앰비언트")
            val choice=UiDialogs.choice(context,"음악 장르",labels,2);var saved=false
            val dialog=UiDialogs.show(context,"새 기록 설정",choice,"저장",true) { saved=true }
            labels.forEach { assertTrue(texts(choice).contains(it)) }
            named(choice,"음악 장르 · 재즈").performClick();assertEquals(8,choice.selectedItemPosition)
            named(dialog.window!!.decorView,"취소").performClick();assertFalse(saved)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun newCoreStatesRetainTheirOwnMessagesAndNeverClaimSavedMusic() {
        val messages=mapOf("BILLING_HELD" to "생성 승인 대기","MATERIAL_REVIEW_REQUIRED" to "재료 확인 필요","REQUEST_HELD" to "작업 확인 필요","PLAN_READY" to "음악 방향 확인","CANCELED" to "취소된 작업","DELETED" to "삭제된 작업","ACCEPTANCE_UNCERTAIN" to "접수 확인 중","FAILED_VALIDATION" to "작업 확인 필요","UNRECOGNIZED" to "작업 확인 필요")
        messages.forEach { (intent,title)->
            val events=Events();val screen=SubmissionScreen(RuntimeEnvironment.getApplication(),events)
            screen.render(state().copy(intentState=intent,status="원래 상태 $intent",controls=state().controls.copy(intentState=intent),tracks=emptyList()));layout(screen)
            assertTrue(texts(screen).contains(title));assertTrue(texts(screen).contains("원래 상태 $intent"));assertFalse(texts(screen).contains("음악 저장 완료"))
            named(screen,"기존 작업 확인").performClick();assertEquals(listOf("generate"),events.calls)
        }
    }

    @Test fun allScreensKeepTouchFramesInsideBothReferenceWidths() {
        val ready=state();val sealed=ready.copy(intentState=null,tracks=emptyList(),controls=ready.controls.copy(intentState=null))
        val cases=listOf(ready,sealed,sealed.copy(capturing=true,records=listOf(record.copy(open=true)),controls=sealed.controls.copy(capturing=true,hasOpen=true)),
            sealed.copy(intentState="ACCEPTANCE_UNCERTAIN"),sealed.copy(intentState="RECONCILING"),sealed.copy(records=emptyList(),selectedId=null),
            ready.copy(page=ScreenPage.RECORDS),ready.copy(page=ScreenPage.LIBRARY))
        for((width,height) in listOf(841 to 605,1682 to 1210)) for(state in cases) {
            val screen=SubmissionScreen(RuntimeEnvironment.getApplication(),Events());screen.render(state);layout(screen,width,height)
            val minimum=if(width==841)76 else 114
            tree(screen).filter { it.isClickable&&it.visibility==View.VISIBLE }.forEach { button->
                assertTrue("${button.contentDescription}: ${button.width}x${button.height}",button.width>=minimum&&button.height>=minimum)
                val position=origin(button)
                assertTrue("${button.contentDescription} outside $width at ${position[0]}",position[0]>=0&&position[0]+button.width<=width)
            }
        }
    }

    @Test fun homePrimaryStaysInPlaceAcrossCaptureSealedPendingAndSaved() {
        for((width,height) in listOf(841 to 605,1682 to 1210)) {
            val ready=state();val sealed=ready.copy(intentState=null,tracks=emptyList(),controls=ready.controls.copy(intentState=null))
            val cases=listOf("기록 마감" to sealed.copy(capturing=true,records=listOf(record.copy(open=true)),controls=sealed.controls.copy(capturing=true,hasOpen=true)),
                "음악 만들기" to sealed,"기존 작업 확인" to sealed.copy(intentState="ACCEPTANCE_UNCERTAIN",status="접수 여부 확인 중 · 원래 작업만 조회하며 새로 생성하지 않습니다"),"보관함으로" to ready)
            var first:IntArray?=null
            val screen=SubmissionScreen(RuntimeEnvironment.getApplication(),Events())
            for((name,state) in cases) {
                screen.render(state);layout(screen,width,height);val position=origin(named(screen,name))
                if(first==null)first=position else assertArrayEquals(first,position)
            }
        }
    }

    @Test fun musicDetailAndBackRestoreWithoutChangingStoredSelection() {
        val events=Events();val screen=SubmissionScreen(RuntimeEnvironment.getApplication(),events)
        screen.render(state(ScreenPage.LIBRARY));layout(screen)
        named(screen,"음악 상세 · ${track.title} · ${record.date}").performClick();layout(screen)
        val saved=android.os.Bundle();screen.saveScroll(saved)
        val restored=SubmissionScreen(RuntimeEnvironment.getApplication(),events)
        restored.restoreScroll(saved);restored.render(state(ScreenPage.LIBRARY));restored.restoreAfterFirstRender();layout(restored)
        assertTrue(texts(restored).contains("음악 상세"));assertTrue(restored.back());layout(restored)
        assertFalse(texts(restored).contains("음악 상세"));assertFalse(restored.back());assertFalse(events.calls.any { it.startsWith("select:") })
    }

    @Test fun readAndPlaybackFailuresRemainVisibleOnEveryPageWithoutInventingEmptyData() {
        for(page in ScreenPage.entries) {
            val screen=SubmissionScreen(RuntimeEnvironment.getApplication(),Events())
            screen.render(state(page).copy(message="저장된 기록을 확인하지 못했습니다 · 기존 기록은 보존됩니다"))
            screen.playback(PlaybackUiState(track.id,"재생 오류",false,notice="재생을 시작할 수 없습니다 · 저장곡은 보존됩니다"));layout(screen)
            assertTrue(texts(screen).any { "저장된 기록을 확인하지 못했습니다" in it })
            assertTrue(texts(screen).any { "재생을 시작할 수 없습니다" in it })
            screen.render(state(page).copy(records=emptyList(),tracks=emptyList(),controls=state().controls.copy(loaded=false)));layout(screen)
            assertFalse("$page loading is not empty",texts(screen).contains("아직 기록이 없어요"));assertFalse("$page loading is not empty",texts(screen).contains("아직 저장된 음악이 없어요"))
        }
    }

    @Test fun pendingFixtureCaptureStatusAndRecoveredOpenRecordRemainExplicit() {
        val screen=SubmissionScreen(RuntimeEnvironment.getApplication(),Events())
        val pending=state().copy(intentState="LOCAL_PENDING",tracks=emptyList(),controls=state().controls.copy(intentState="LOCAL_PENDING"))
        screen.render(pending);layout(screen);assertTrue(texts(screen).contains("대역 · 새 AI 생성 아님"))
        screen.render(pending.copy(fixture=false));layout(screen);assertFalse(texts(screen).contains("대역 · 새 AI 생성 아님"))
        val capturing=pending.copy(intentState=null,capturing=true,records=listOf(record.copy(open=true)),captureStatus="SDK 연결 확인 필요",controls=pending.controls.copy(capturing=true,hasOpen=true,sealed=false))
        screen.render(capturing);layout(screen);assertTrue(texts(screen).contains("SDK 연결 확인 필요"))
        screen.render(capturing.copy(capturing=false,controls=capturing.controls.copy(capturing=false)));layout(screen)
        assertTrue(texts(screen).contains("마감 전 기록"));assertTrue(tree(screen).any { it.contentDescription=="기록 이어가기" })
    }
}
