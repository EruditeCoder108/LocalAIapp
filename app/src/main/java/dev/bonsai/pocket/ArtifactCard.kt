package dev.bonsai.pocket

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable fun ArtifactCard(file:CodeArtifact,companions:List<CodeArtifact>,canvas:Boolean,streaming:Boolean,downloading:Boolean,onDownload:(CodeArtifact)->Unit) {
    val colors=MaterialTheme.colorScheme
    var source by rememberSaveable(file.name,file.source) {mutableStateOf(file.source)}
    var editor by rememberSaveable(file.name) {mutableStateOf(false)}
    var inlinePreview by rememberSaveable(file.name,canvas) {mutableStateOf(canvas)}
    var previewTab by rememberSaveable(file.name,canvas) {mutableStateOf(canvas && file.previewable)}
    val edited=file.copy(source=source)
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Surface(onClick={if(!streaming) editor=true},shape=RoundedCornerShape(28.dp),color=colors.surfaceContainerHigh,
            border=BorderStroke(1.dp,colors.outlineVariant),modifier=Modifier.fillMaxWidth().testTag("artifact-${file.name}")) {
            Row(Modifier.padding(start=18.dp,end=7.dp,top=7.dp,bottom=7.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.InsertDriveFile,null,Modifier.size(23.dp),tint=colors.onSurfaceVariant)
                Column(Modifier.weight(1f).padding(horizontal=12.dp,vertical=5.dp)) {
                    Text(file.name,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(if(streaming) "Writing file…" else "${file.extension.uppercase()} · ${"%.1f".format(java.util.Locale.US,source.toByteArray().size/1024.0)} KB${if(!file.complete) " · Partial" else ""}",
                        style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
                }
                IconButton(onClick={onDownload(edited)},enabled=!streaming && !downloading,modifier=Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.FileDownload,"Download ${file.name}",Modifier.size(21.dp))
                }
            }
        }
        if(file.previewable && !streaming && file.complete) {
            if(inlinePreview) {
                CanvasPreview(edited,companions,Modifier.fillMaxWidth().height(320.dp))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("CANVAS · OFFLINE",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant,modifier=Modifier.weight(1f))
                    TextButton(onClick={inlinePreview=false}) {Text("Hide")}
                    TextButton(onClick={previewTab=true;editor=true}) {Icon(Icons.Outlined.OpenInFull,null,Modifier.size(16.dp));Spacer(Modifier.width(8.dp));Text("Expand")}
                }
            } else TextButton(onClick={inlinePreview=true}) {Icon(Icons.Outlined.PlayArrow,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Run canvas")}
        }
    }
    if(editor) Dialog(onDismissRequest={editor=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),color=colors.background) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={editor=false}) {Icon(Icons.Outlined.Close,"Close file")}
                    Text(file.name,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                    IconButton(onClick={onDownload(edited)},enabled=!downloading) {Icon(Icons.Outlined.FileDownload,"Download ${file.name}")}
                }
                Row(Modifier.padding(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected=!previewTab,onClick={previewTab=false},label={Text("Code")})
                    if(file.previewable && file.complete) FilterChip(selected=previewTab,onClick={previewTab=true},label={Text("Preview")})
                }
                if(previewTab && file.previewable) CanvasPreview(edited,companions,Modifier.fillMaxWidth().weight(1f).padding(12.dp))
                else {
                    OutlinedTextField(value=source,onValueChange={if(it.length<=200_000) source=it},modifier=Modifier.fillMaxWidth().weight(1f).padding(16.dp).testTag("artifactSource"),
                        textStyle=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace,fontSize=13.sp,lineHeight=20.sp),label={Text("Source")})
                    Text("Edits apply to this preview and download.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant,modifier=Modifier.padding(horizontal=20.dp,vertical=10.dp))
                }
            }
        }
    }
}
