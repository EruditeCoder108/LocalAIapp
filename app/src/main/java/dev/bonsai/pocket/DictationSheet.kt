package dev.bonsai.pocket

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable fun VoiceMatrix(level:Float,listening:Boolean,modifier:Modifier=Modifier) {
    val color=MaterialTheme.colorScheme.onSurface
    var phase by remember {mutableFloatStateOf(0f)}
    LaunchedEffect(listening) {if(listening) {val start=withFrameNanos {it};while(true) withFrameNanos {phase=(it-start)/1_000_000_000f}}}
    Canvas(modifier.semantics {contentDescription=if(listening) "Microphone activity" else "Dictation"}) {
        val cell=min(size.width/23,size.height/9);val left=(size.width-cell*23)/2;val top=(size.height-cell*9)/2
        for(x in 0 until 23) {
            // Time varies the pattern; measured microphone energy determines its height.
            val strength=if(listening) level*(.4f+.6f*abs(sin(x*.58f-phase*3))) else 0f
            val height=1+strength*4
            for(y in 0 until 9) {
                val active=abs(y-4)<=height
                drawCircle(color.copy(alpha=if(active) .9f else .1f),cell*.20f,Offset(left+(x+.5f)*cell,top+(y+.5f)*cell))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DictationSheet(voice:DictationController,onStart:()->Unit,onReplaceDraft:(String)->Unit,onOpenPermissions:()->Unit) {
    if(!voice.busy) return
    val colors=MaterialTheme.colorScheme
    val clipboard=LocalClipboardManager.current
    ModalBottomSheet(onDismissRequest=voice::cancel,containerColor=colors.surface,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=32.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text("Dictation",style=MaterialTheme.typography.headlineSmall)
            Text("PHONON 2.5 · ENGLISH",style=MaterialTheme.typography.labelSmall.copy(fontFamily=FontFamily.Monospace),color=colors.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))
            VoiceMatrix(voice.level,voice.phase==DictationPhase.LISTENING,Modifier.fillMaxWidth().height(112.dp).padding(vertical=14.dp).testTag("voiceMatrix"))
            when(voice.phase) {
                DictationPhase.SETUP->{
                    Text("Speak into your message draft. Audio stays on your phone and is deleted after transcription.",color=colors.onSurfaceVariant)
                    Text("164 MB download · 178 MB installed\nEnglish dictation; Hindi is not supported by this model.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant,modifier=Modifier.padding(vertical=18.dp))
                    Button(onClick=voice::download,modifier=Modifier.fillMaxWidth().testTag("downloadDictation")) {Text("Download dictation model")}
                }
                DictationPhase.READY->{
                    Text("Up to 30 seconds. Tap Done when you finish. The transcript goes into your draft for review.",color=colors.onSurfaceVariant)
                    Button(onClick=onStart,modifier=Modifier.fillMaxWidth().padding(top=20.dp).testTag("startDictation")) {Text("Start dictation")}
                }
                DictationPhase.LISTENING->{
                    Text("Listening…",style=MaterialTheme.typography.titleMedium)
                    Text("${voice.seconds.toInt()} / 30 seconds",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant,modifier=Modifier.padding(top=8.dp,bottom=20.dp))
                    Button(onClick=voice::finishRecording,modifier=Modifier.fillMaxWidth().testTag("finishDictation")) {Text("Done")}
                }
                DictationPhase.DOWNLOADING->{
                    Text(voice.status,color=colors.onSurfaceVariant)
                    LinearProgressIndicator(progress={voice.progress},modifier=Modifier.fillMaxWidth().padding(vertical=20.dp))
                }
                DictationPhase.ERROR->{
                    Text(voice.status,color=colors.onSurfaceVariant)
                    Button(onClick=voice::retry,modifier=Modifier.fillMaxWidth().padding(top=20.dp)) {Text("Try again")}
                    if(voice.permissionProblem) TextButton(onClick=onOpenPermissions) {Text("Open app permissions")}
                }
                DictationPhase.RESULT->{
                    Text("Your draft is full. Copy this transcript or replace the draft to keep it.",color=colors.onSurfaceVariant)
                    OutlinedTextField(voice.transcript,{},readOnly=true,modifier=Modifier.fillMaxWidth().heightIn(max=200.dp).padding(vertical=14.dp))
                    Button(onClick={onReplaceDraft(voice.transcript);voice.consume()},modifier=Modifier.fillMaxWidth()) {Text("Replace draft with transcript")}
                    TextButton(onClick={clipboard.setText(AnnotatedString(voice.transcript))}) {Text("Copy transcript")}
                }
                else->{Text(voice.status,color=colors.onSurfaceVariant);LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical=20.dp))}
            }
            if(voice.phase!=DictationPhase.CANCELLING) TextButton(onClick=voice::cancel,modifier=Modifier.fillMaxWidth().padding(top=8.dp)) {Text(if(voice.phase in listOf(DictationPhase.SETUP,DictationPhase.READY,DictationPhase.ERROR)) "Close" else "Cancel")}
        }
    }
}
