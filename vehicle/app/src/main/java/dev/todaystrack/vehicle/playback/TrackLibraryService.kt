package dev.todaystrack.vehicle.playback

import android.net.Uri
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dev.todaystrack.vehicle.TodaysTrackApplication
import dev.todaystrack.vehicle.generation.Track
import kotlinx.coroutines.*
import java.io.File

@androidx.annotation.OptIn(UnstableApi::class)
class TrackLibraryService : MediaLibraryService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    override fun onCreate() {
        super.onCreate()
        player=ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
            setHandleAudioBecomingNoisy(true)
        }
        session=MediaLibrarySession.Builder(this,player,object : MediaLibrarySession.Callback {
            override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
                val item=MediaItem.Builder().setMediaId("saved-tracks").setMediaMetadata(MediaMetadata.Builder().setTitle("오늘의 트랙")
                    .setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()).build()
                return Futures.immediateFuture(LibraryResult.ofItem(item,params))
            }
            override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {
                val future=SettableFuture.create<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>>()
                scope.launch {
                    val tracks=(application as TodaysTrackApplication).database.generationDao().tracks()
                    val items=tracks.filter { File(it.path).isFile }.map { mediaItem(it) }.drop((page*pageSize).coerceAtLeast(0)).take(pageSize.coerceIn(1,100))
                    future.set(LibraryResult.ofItemList(items,params))
                }
                return future
            }
            override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> {
                val future=SettableFuture.create<MutableList<MediaItem>>()
                scope.launch {
                    val tracks=(application as TodaysTrackApplication).database.generationDao().tracks().associateBy { it.id }
                    future.set(mediaItems.mapNotNull { tracks[it.mediaId]?.let(::mediaItem) }.toMutableList())
                }
                return future
            }
        }).build()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session
    override fun onDestroy() { session.release();player.release();scope.cancel();super.onDestroy() }
    companion object {
        fun mediaItem(track: Track): MediaItem = MediaItem.Builder().setMediaId(track.id).setUri(Uri.fromFile(File(track.path)))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(if(track.provenance in setOf("FIXTURE","LOCAL_TEST")) "기존 음원 대역 · 새 생성 아님" else "AI 음악 · SDK 합성 주행 기록")
                .setIsPlayable(true).setIsBrowsable(false).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build()).build()
    }
}
