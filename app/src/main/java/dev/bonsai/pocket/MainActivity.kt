package dev.bonsai.pocket

import android.graphics.BitmapFactory
import android.os.Bundle
import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Signal=Color(0xFFE53935)
private val Geist=FontFamily(Font(R.font.geist))
private val Dark=darkColorScheme(primary=Color.White,onPrimary=Color.Black,primaryContainer=Color(0xFF252525),onPrimaryContainer=Color.White,secondary=Color(0xFFBDBDBD),secondaryContainer=Color(0xFF202020),onSecondaryContainer=Color.White,background=Color(0xFF080808),onBackground=Color(0xFFF4F4F4),surface=Color(0xFF101010),onSurface=Color(0xFFF4F4F4),surfaceVariant=Color(0xFF252525),onSurfaceVariant=Color(0xFFA1A1A1),outline=Color(0xFF888888),outlineVariant=Color(0xFF303030),surfaceContainer=Color(0xFF181818),surfaceContainerLow=Color(0xFF121212),surfaceContainerHigh=Color(0xFF242424),error=Signal)
private val Light=lightColorScheme(primary=Color.Black,onPrimary=Color.White,primaryContainer=Color(0xFFE6E6E6),onPrimaryContainer=Color.Black,secondary=Color(0xFF555555),secondaryContainer=Color(0xFFEEEEEE),onSecondaryContainer=Color.Black,background=Color(0xFFF6F6F6),onBackground=Color(0xFF101010),surface=Color(0xFFFAFAFA),onSurface=Color(0xFF101010),surfaceVariant=Color(0xFFE8E8E8),onSurfaceVariant=Color(0xFF656565),outlineVariant=Color(0xFFD9D9D9),surfaceContainer=Color(0xFFEEEEEE),surfaceContainerLow=Color(0xFFF3F3F3),surfaceContainerHigh=Color(0xFFE2E2E2),error=Signal)
private val AppType=Typography().let { t -> t.copy(bodyLarge=t.bodyLarge.copy(fontFamily=Geist,lineHeight=26.sp),bodyMedium=t.bodyMedium.copy(fontFamily=Geist),bodySmall=t.bodySmall.copy(fontFamily=Geist),titleLarge=t.titleLarge.copy(fontFamily=Geist),titleMedium=t.titleMedium.copy(fontFamily=Geist),titleSmall=t.titleSmall.copy(fontFamily=Geist),headlineSmall=t.headlineSmall.copy(fontFamily=Geist),headlineMedium=t.headlineMedium.copy(fontFamily=Geist),labelLarge=t.labelLarge.copy(fontFamily=Geist),labelMedium=t.labelMedium.copy(fontFamily=Geist),labelSmall=t.labelSmall.copy(fontFamily=Geist)) }

class MainActivity:ComponentActivity() {
    private val vm:BonsaiViewModel by viewModels()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            val dark=when(vm.theme) { 1->false; 2->true; else->isSystemInDarkTheme() }
            SideEffect { enableEdgeToEdge(statusBarStyle=if(dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),navigationBarStyle=if(dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT)) }
            MaterialTheme(colorScheme=if(dark) Dark else Light,typography=AppType) { BonsaiApp(vm) }
        }
    }
    override fun onStart() { super.onStart(); vm.setForeground(true); vm.refreshModel() }
    override fun onStop() { vm.setForeground(false); super.onStop() }
}

@Composable private fun Micro(text:String,modifier:Modifier=Modifier,color:Color=MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text,modifier,style=MaterialTheme.typography.labelSmall.copy(fontFamily=FontFamily.Monospace,letterSpacing=1.2.sp),color=color)
}
private var cachedBrandBitmap:ImageBitmap?=null
@Composable private fun BrandMark(modifier:Modifier) {
    val resources=LocalContext.current.resources
    val bitmap=remember { cachedBrandBitmap ?: BitmapFactory.decodeResource(resources,R.drawable.bonsai_mark,BitmapFactory.Options().apply { inSampleSize=2; inScaled=false }).asImageBitmap().also {cachedBrandBitmap=it} }
    Canvas(modifier.clip(CircleShape).semantics {contentDescription="Ember"}) {
        val inset=(bitmap.width*0.17f).toInt()
        drawImage(bitmap,srcOffset=IntOffset(inset,inset),srcSize=IntSize(bitmap.width-inset*2,bitmap.height-inset*2),dstSize=IntSize(size.width.toInt(),size.height.toInt()))
    }
}
@Composable private fun pressFeedback():Pair<MutableInteractionSource,Modifier> {
    val source=remember {MutableInteractionSource()}
    val pressed by source.collectIsPressedAsState()
    val scale=animateFloatAsState(if(pressed) 0.96f else 1f,animationSpec=tween(110),label="press")
    return source to Modifier.graphicsLayer {scaleX=scale.value;scaleY=scale.value}
}
private fun Modifier.dotGrid(color:Color)=drawWithCache {
    val path=Path(); val spacing=24.dp.toPx(); val radius=0.7.dp.toPx()
    var y=spacing/2
    while(y<size.height) { var x=spacing/2; while(x<size.width) { path.addOval(Rect(x-radius,y-radius,x+radius,y+radius)); x+=spacing }; y+=spacing }
    onDrawBehind { drawPath(path,color.copy(alpha=0.11f)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BonsaiApp(vm:BonsaiViewModel) {
    val colors=MaterialTheme.colorScheme
    val scope=rememberCoroutineScope()
    val drawer=rememberDrawerState(DrawerValue.Closed)
    val snack=remember { SnackbarHostState() }
    var sheet by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var draft by rememberSaveable(vm.currentId) { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var webEnabled by rememberSaveable {mutableStateOf(false)}
    var followRequest by remember {mutableIntStateOf(0)}
    var exportText by remember {mutableStateOf<String?>(null)}
    var pendingExport by rememberSaveable {mutableStateOf<String?>(null)}
    var exportFormat by rememberSaveable {mutableStateOf(ResponseFormat.PDF)}
    val filePicker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text=pendingExport;pendingExport=null
        if(result.resultCode==Activity.RESULT_OK && text!=null) result.data?.data?.let {uri->vm.exportResponse(uri,text,exportFormat) {scope.launch {snack.showSnackbar("File saved")}}}
    }
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current
    val haptic=LocalHapticFeedback.current
    var lastHapticAttachment by rememberSaveable {mutableStateOf("")}
    val sendPress=pressFeedback()
    val loadPress=pressFeedback()
    val photoPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::attachImage) }
    val modelPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importModel) }
    val visionPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importVision) }
    val messages=vm.current.messages
    fun closeDrawer() { scope.launch { drawer.close() } }
    fun showSheet(name:String) {focus.clearFocus();keyboard?.hide();sheet=name}
    fun send() { if(vm.send(draft,webEnabled)) { draft="";webEnabled=false;followRequest++; focus.clearFocus(); keyboard?.hide(); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
    LaunchedEffect(vm.pendingImage) {if(vm.pendingImage.isNotEmpty() && vm.pendingImage!=lastHapticAttachment) {lastHapticAttachment=vm.pendingImage;haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}}
    LaunchedEffect(vm.currentId) {webEnabled=false}
    ModalNavigationDrawer(drawerState=drawer,gesturesEnabled=!vm.generating,drawerContent={
        ModalDrawerSheet(drawerContainerColor=colors.background,modifier=Modifier.width(310.dp)) {
            Row(Modifier.fillMaxWidth().padding(start=18.dp,end=12.dp,top=12.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically) {
                BrandMark(Modifier.size(32.dp)); Spacer(Modifier.width(10.dp)); Micro("EMBER",Modifier.weight(1f),colors.onSurface)
                IconButton(onClick={closeDrawer()}) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Close chat drawer") }
            }
            Button(onClick={vm.newChat();closeDrawer()},enabled=!vm.generating && !vm.preparingImage,colors=ButtonDefaults.buttonColors(containerColor=Signal,contentColor=Color.White),modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp).height(48.dp),shape=RoundedCornerShape(16.dp)) { Icon(Icons.Outlined.Add,null,Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text("New chat") }
            OutlinedTextField(query,{query=it},placeholder={Text("Search chats")},leadingIcon={Icon(Icons.Outlined.Search,null,Modifier.size(18.dp))},singleLine=true,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().padding(20.dp))
            Micro("CONVERSATIONS",Modifier.padding(start=24.dp,bottom=12.dp))
            val chats=remember(vm.chats,query) { vm.chats.filter { it.messages.isNotEmpty() && (query.isBlank() || it.title.contains(query,true)) } }
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(start=12.dp,end=12.dp,bottom=12.dp)) {
                items(chats,key={it.id}) { chat ->
                    Surface(onClick={vm.openChat(chat.id);closeDrawer()},enabled=!vm.generating && !vm.preparingImage,color=colors.onSurface.copy(alpha=if(chat.id==vm.currentId) 0.12f else 0.055f),border=androidx.compose.foundation.BorderStroke(1.dp,colors.onSurface.copy(alpha=0.045f)),shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                        Row(Modifier.padding(start=16.dp,top=10.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text(chat.title,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.weight(1f))
                            IconButton(onClick={deleteId=chat.id},enabled=!vm.generating) { Icon(Icons.Outlined.MoreHoriz,"Options for ${chat.title}",Modifier.size(18.dp)) }
                        }
                    }
                }
                if(chats.isEmpty()) item { Text(if(query.isBlank()) "Your chats will appear here." else "No matching chats.",color=colors.onSurfaceVariant,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(16.dp)) }
            }
            HorizontalDivider(color=colors.outlineVariant)
            TextButton(onClick={closeDrawer();showSheet("settings")},modifier=Modifier.fillMaxWidth().padding(12.dp)) { Icon(Icons.Outlined.Tune,null,Modifier.size(18.dp)); Spacer(Modifier.width(12.dp)); Text("Settings") }
        }
    }) {
        Scaffold(containerColor=colors.background,snackbarHost={SnackbarHost(snack)},topBar={
            Surface(color=colors.background.copy(alpha=0.97f)) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={focus.clearFocus(); keyboard?.hide(); scope.launch { drawer.open() }}) { Icon(Icons.Outlined.Menu,"Open chat drawer") }
                    TextButton(onClick={showSheet("models")},enabled=vm.canSwitch,modifier=Modifier.testTag("modelPicker"),contentPadding=PaddingValues(horizontal=8.dp)) {
                        Micro(vm.selectedModel.header,color=colors.onSurface)
                        Icon(Icons.Outlined.KeyboardArrowDown,null,Modifier.padding(start=5.dp).size(17.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick={showSheet("settings")}) { Icon(Icons.Outlined.Tune,"Settings",Modifier.size(21.dp)) }
                    IconButton(onClick=vm::newChat,enabled=!vm.generating && !vm.preparingImage) { Icon(Icons.Outlined.Edit,"New chat",Modifier.size(21.dp)) }
                }
            }
        },bottomBar={
            Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal=16.dp)) {
                if(vm.modelState==ModelState.LOADING || vm.modelState==ModelState.IMPORTING) {
                    if(vm.modelState==ModelState.IMPORTING) LinearProgressIndicator(progress={vm.importProgress},modifier=Modifier.fillMaxWidth().height(2.dp),color=Signal,trackColor=colors.outlineVariant)
                    else HorizontalDivider(thickness=2.dp,color=Signal)
                    Micro(vm.status.uppercase(),Modifier.padding(vertical=10.dp))
                    if(vm.downloading) TextButton(onClick=vm::cancelDownload) {Text("Pause download")}
                } else if(messages.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(start=4.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).background(if(vm.modelState==ModelState.READY) Signal else colors.outline,CircleShape))
                        Micro(if(vm.generating) vm.status.uppercase() else "${vm.selectedModel.shortName} · ${if(vm.modelState==ModelState.READY) "ON DEVICE" else "STANDBY"}",Modifier.padding(start=8.dp).weight(1f))
                        if(vm.modelState==ModelState.ASLEEP) TextButton(onClick=vm::load) { Text("Load model") }
                        if(vm.modelState==ModelState.READY && !vm.generating) IconButton(onClick=vm::unload,modifier=Modifier.size(38.dp)) { Icon(Icons.Outlined.PowerSettingsNew,"Unload model",Modifier.size(18.dp)) }
                    }
                }
                Surface(shape=RoundedCornerShape(26.dp),color=colors.surfaceContainer.copy(alpha=0.96f),border=androidx.compose.foundation.BorderStroke(1.dp,colors.outlineVariant),modifier=Modifier.fillMaxWidth()) {
                    Column {
                        if(webEnabled) Row(Modifier.padding(start=16.dp,top=10.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Language,null,Modifier.size(16.dp),tint=Signal)
                            Text("Search web for this message",style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(start=8.dp).weight(1f))
                            IconButton(onClick={webEnabled=false},modifier=Modifier.size(30.dp)) {Icon(Icons.Outlined.Close,"Turn off web search",Modifier.size(16.dp))}
                        }
                        if(vm.pendingImage.isNotEmpty()) Row(Modifier.padding(start=14.dp,top=12.dp,end=12.dp),verticalAlignment=Alignment.CenterVertically) {
                            ImageThumbnail(vm.imageFile(vm.pendingImage),Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                            Text("Image attached",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(start=12.dp).weight(1f))
                            IconButton(onClick=vm::clearAttachment) { Icon(Icons.Outlined.Close,"Remove attachment",Modifier.size(18.dp)) }
                        }
                        Row(Modifier.padding(6.dp),verticalAlignment=Alignment.CenterVertically) {
                            Box {
                                var toolsOpen by remember {mutableStateOf(false)}
                                IconButton(onClick={toolsOpen=true},enabled=!vm.generating && !vm.preparingImage,modifier=Modifier.size(46.dp)) {Icon(Icons.Outlined.Add,"Chat tools",Modifier.size(23.dp))}
                                DropdownMenu(expanded=toolsOpen,onDismissRequest={toolsOpen=false}) {
                                    DropdownMenuItem(text={Text("Attach image")},leadingIcon={Icon(Icons.Outlined.Image,null)},enabled=vm.visionInstalled,onClick={toolsOpen=false;photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))})
                                    DropdownMenuItem(text={Text(if(webEnabled) "Turn off web search" else "Search web")},leadingIcon={Icon(Icons.Outlined.Language,null)},onClick={toolsOpen=false;webEnabled=!webEnabled;if(webEnabled) scope.launch {snack.showSnackbar("Only this message's search words go online. Chats, memories and images stay on your phone.")}})
                                    DropdownMenuItem(text={Text("Memory")},leadingIcon={Icon(Icons.Outlined.BookmarkBorder,null)},onClick={toolsOpen=false;showSheet("memory")})
                                }
                            }
                            TextField(draft,{if(it.length<=8000) draft=it},modifier=Modifier.weight(1f).heightIn(min=46.dp,max=150.dp).testTag("messageInput"),placeholder={Text(if(vm.preparingImage) "Preparing image…" else "Message Ember",style=MaterialTheme.typography.bodyLarge)},textStyle=MaterialTheme.typography.bodyLarge,colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Send),keyboardActions=KeyboardActions(onSend={send()}),maxLines=5)
                            FilledIconButton(onClick={if(vm.generating) vm.stop() else send()},enabled=vm.generating || ((draft.isNotBlank() || vm.pendingImage.isNotEmpty()) && !vm.preparingImage && (vm.modelState==ModelState.READY || (!webEnabled && MemoryStore.rememberCommand(draft)!=null && vm.memoryReady && !vm.memoryBusy))),interactionSource=sendPress.first,modifier=Modifier.size(46.dp).then(sendPress.second),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Signal,contentColor=Color.White,disabledContainerColor=colors.onSurface.copy(alpha=0.08f),disabledContentColor=colors.onSurface.copy(alpha=0.3f))) { Icon(if(vm.generating) Icons.Outlined.Stop else Icons.Outlined.ArrowUpward,if(vm.generating) "Stop response" else "Send message",Modifier.size(21.dp)) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }) { padding ->
            if(messages.isEmpty()) Column(Modifier.fillMaxSize().padding(padding).dotGrid(colors.onSurface).padding(horizontal=24.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        BrandMark(Modifier.size(154.dp))
                        Spacer(Modifier.height(14.dp)); MatrixTitle(Modifier.width(228.dp).height(44.dp))
                    }
                }
                Surface(color=colors.surfaceContainer.copy(alpha=0.9f),shape=RoundedCornerShape(22.dp),border=androidx.compose.foundation.BorderStroke(1.dp,colors.outlineVariant),modifier=Modifier.fillMaxWidth().padding(bottom=24.dp)) {
                    Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Micro(vm.selectedModel.header,color=colors.onSurface); Text("${vm.selectedModel.size} · ${if(vm.modelState==ModelState.READY) "Ready" else if(vm.modelState==ModelState.MISSING) "Not installed" else "On device"}",color=colors.onSurfaceVariant,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=7.dp)) }
                        Button(onClick={if(vm.modelState==ModelState.READY) vm.unload() else if(vm.modelState==ModelState.MISSING) vm.downloadModel() else vm.load()},enabled=vm.modelState!=ModelState.LOADING && vm.modelState!=ModelState.IMPORTING,interactionSource=loadPress.first,modifier=loadPress.second,colors=ButtonDefaults.buttonColors(containerColor=colors.onSurface,contentColor=colors.background),shape=RoundedCornerShape(14.dp),contentPadding=PaddingValues(horizontal=16.dp,vertical=12.dp)) { Text(if(vm.modelState==ModelState.READY) "Unload" else if(vm.modelState==ModelState.MISSING) "Download" else "Load model") }
                    }
                }
            } else ConversationTimeline(vm.currentId,messages.size,messages.lastOrNull()?.text?.length ?: 0,followRequest,Modifier.fillMaxSize().padding(padding)) {
                    itemsIndexed(messages,key={i,_->"${vm.currentId}:$i"}) { i,msg -> MessageView(msg,vm.generating && i==messages.lastIndex,vm,onCopy={scope.launch {snack.showSnackbar("Copied")}},onExport={exportText=msg.text;showSheet("export")}) }
                }
        }
    }
    vm.error?.let { AlertDialog(onDismissRequest=vm::dismissError,title={Text("Could not complete that")},text={Text(it)},confirmButton={TextButton(onClick=vm::dismissError) {Text("OK")}}) }
    deleteId?.let { id -> AlertDialog(onDismissRequest={deleteId=null},title={Text("Delete conversation?")},text={Text("This removes the chat and its attached images from this phone.")},confirmButton={TextButton(onClick={vm.deleteChat(id);deleteId=null}) {Text("Delete",color=Signal)}},dismissButton={TextButton(onClick={deleteId=null}) {Text("Cancel")}}) }
    if(sheet!=null) ModalBottomSheet(onDismissRequest={sheet=null},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=colors.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=36.dp)) {
            when(sheet) {
                "models" -> ModelSheet(vm,onSelected={sheet=null},onImport={sheet=null;modelPicker.launch(arrayOf("*/*"))})
                "memory" -> MemorySheet(vm)
                "export" -> {
                    Text("Save response",style=MaterialTheme.typography.headlineSmall)
                    Text("Choose a format, then choose where to save it.",color=colors.onSurfaceVariant,modifier=Modifier.padding(top=8.dp,bottom=18.dp))
                    ResponseFormat.entries.filter {it!=ResponseFormat.CODE || ResponseFiles.code(exportText.orEmpty())!=null}.forEach {format->
                        TextButton(onClick={
                            val text=exportText ?: return@TextButton
                            pendingExport=text;exportFormat=format;sheet=null
                            val extension=ResponseFiles.extension(text,format)
                            filePicker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(format.mime).putExtra(Intent.EXTRA_TITLE,"Ember-response-${System.currentTimeMillis()}.$extension"))
                        },enabled=!vm.exporting,modifier=Modifier.fillMaxWidth()) {Icon(Icons.Outlined.FileDownload,null,Modifier.size(18.dp));Spacer(Modifier.width(14.dp));Text(if(format==ResponseFormat.CODE) "First code block (.${ResponseFiles.extension(exportText.orEmpty(),format)})" else format.label,Modifier.weight(1f))}
                    }
                    TextButton(onClick={sheet=null},modifier=Modifier.fillMaxWidth()) {Text("Cancel")}
                }
                else -> Settings(vm,onModels={sheet="models"},onMemory={sheet="memory"},onImport={sheet=null;modelPicker.launch(arrayOf("*/*"))},onVisionImport={sheet=null;visionPicker.launch(arrayOf("*/*"))})
            }
        }
    }
}

@Composable private fun MatrixTitle(modifier:Modifier) {
    val color=MaterialTheme.colorScheme.onSurface
    val glyphs=remember { listOf("11111/10000/10000/11110/10000/10000/11111","10001/11011/10101/10101/10001/10001/10001","11110/10001/10001/11110/10001/10001/11110","11111/10000/10000/11110/10000/10000/11111","11110/10001/10001/11110/10100/10010/10001").map { it.split('/') } }
    Canvas(modifier.semantics {contentDescription="EMBER"}) {
        val cell=minOf(size.width/29f,size.height/7f)
        val left=(size.width-cell*29f)/2f
        glyphs.forEachIndexed { letter,rows -> rows.forEachIndexed { row,bits -> bits.forEachIndexed { col,ch -> if(ch=='1') drawCircle(color,cell*0.29f,Offset(left+(letter*6+col+0.5f)*cell,(row+0.5f)*cell)) } } }
    }
}
@Composable private fun ImageThumbnail(file:File,modifier:Modifier) {
    val bitmap by produceState<ImageBitmap?>(null,file.absolutePath) { value=withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.absolutePath,BitmapFactory.Options().apply {inSampleSize=4;inPreferredConfig=android.graphics.Bitmap.Config.RGB_565})?.asImageBitmap() } }
    bitmap?.let { Image(it,"Attached image",modifier,contentScale=ContentScale.Crop) }
}
@Composable private fun MessageView(msg:Message,streaming:Boolean,vm:BonsaiViewModel,onCopy:()->Unit,onExport:()->Unit) {
    val c=MaterialTheme.colorScheme; val clipboard=LocalClipboardManager.current
    val uriHandler=LocalUriHandler.current
    if(msg.role=="user") Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
        Surface(shape=RoundedCornerShape(22.dp,22.dp,5.dp,22.dp),color=c.surfaceContainerHigh,modifier=Modifier.widthIn(max=324.dp)) {
            Column(Modifier.padding(15.dp)) {
                if(msg.image.isNotEmpty()) { ImageThumbnail(vm.imageFile(msg.image),Modifier.size(192.dp).clip(RoundedCornerShape(12.dp)));Spacer(Modifier.height(10.dp)) }
                SelectionContainer { Text(msg.text,style=MaterialTheme.typography.bodyLarge) }
            }
        }
    } else Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).background(Signal,CircleShape)); Micro(Models.find(msg.modelId).header,Modifier.padding(start=8.dp))
        }
        Spacer(Modifier.height(13.dp))
        if(msg.text.isBlank()) Text(if(vm.current.messages.any {it.image.isNotEmpty()}) "Reading your image…" else "Reading your message…",color=c.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
        else SelectionContainer { MarkdownText(msg.text) }
        if(msg.sources.isNotEmpty()) Column(Modifier.padding(top=12.dp)) {
            Micro("WEB SOURCES")
            msg.sources.forEachIndexed {i,source->TextButton(onClick={runCatching {uriHandler.openUri(source.url)}},contentPadding=PaddingValues(horizontal=10.dp,vertical=10.dp),modifier=Modifier.fillMaxWidth()) {
                Text("[${i+1}] ${source.title}",maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f));Icon(Icons.Outlined.OpenInNew,null,Modifier.size(14.dp))
            }}
        }
        if(!streaming && msg.text.isNotBlank()) Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically) {
            var details by remember(msg.note) {mutableStateOf(false)}
            if(details) Text(msg.note,style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant,modifier=Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            IconButton(onClick={details=!details},modifier=Modifier.size(36.dp)) {Icon(Icons.Outlined.Info,"Response details",Modifier.size(16.dp),tint=c.onSurfaceVariant)}
            IconButton(onClick={clipboard.setText(AnnotatedString(msg.text));onCopy()},modifier=Modifier.size(36.dp)) {Icon(Icons.Outlined.ContentCopy,"Copy response",Modifier.size(16.dp),tint=c.onSurfaceVariant)}
            IconButton(onClick=onExport,enabled=!vm.exporting,modifier=Modifier.size(36.dp)) {Icon(Icons.Outlined.FileDownload,"Save response",Modifier.size(17.dp),tint=c.onSurfaceVariant)}
        }
    }
}
@Composable private fun MarkdownText(text:String) {
    val c=MaterialTheme.colorScheme
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        text.split("```").forEachIndexed { index,block -> if(block.isNotBlank()) {
            if(index%2==1) Surface(shape=RoundedCornerShape(12.dp),color=c.surfaceContainer,modifier=Modifier.fillMaxWidth()) {Text(block.trim(),fontFamily=FontFamily.Monospace,fontSize=13.sp,lineHeight=20.sp,modifier=Modifier.padding(14.dp))}
            else Text(buildAnnotatedString {val raw=block.trim().replace(Regex("(?m)^#{1,6} +"),"");var pos=0;Regex("\\*\\*(.+?)\\*\\*").findAll(raw).forEach {m->append(raw.substring(pos,m.range.first));withStyle(SpanStyle(fontWeight=FontWeight.SemiBold)) {append(m.groupValues[1])};pos=m.range.last+1};append(raw.substring(pos))},style=MaterialTheme.typography.bodyLarge)
        } }
    }
}
@Composable private fun ModelSheet(vm:BonsaiViewModel,onSelected:()->Unit,onImport:()->Unit) {
    val haptic=LocalHapticFeedback.current
    Text("Choose your model",style=MaterialTheme.typography.headlineSmall)
    Text("Only one model stays in memory. Switch here at any time between replies.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=10.dp,bottom=24.dp))
    Models.all.forEach { model ->
        val selected=model.id==vm.selectedModel.id
        Surface(onClick={if(!selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);vm.selectModel(model);onSelected()},enabled=vm.canSwitch,color=MaterialTheme.colorScheme.surfaceContainer,shape=RoundedCornerShape(20.dp),border=androidx.compose.foundation.BorderStroke(1.dp,if(selected) Signal else MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth().padding(bottom=12.dp).testTag("select-${model.id}")) {
            Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {Text(model.displayName,style=MaterialTheme.typography.titleMedium);Text("${model.size} · ${if(vm.installed(model)) "Installed" else "Not installed"}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=7.dp))}
                RadioButton(selected=selected,onClick=null,colors=RadioButtonDefaults.colors(selectedColor=Signal))
            }
        }
    }
    val installedBytes=Models.all.sumOf { (if(vm.installed(it)) it.bytes else 0L)+(if(vm.visionFile(it).length()==it.visionBytes) it.visionBytes else 0L) }
    Micro("INSTALLED MODELS · ${"%.2f".format(java.util.Locale.US,installedBytes/1e9)} GB",Modifier.padding(top=8.dp,bottom=16.dp))
    Text("Downloads come directly from PrismML on Hugging Face. Keep Ember open; leaving the app pauses the transfer. Downloading does not load a model into RAM.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(vm.modelState==ModelState.MISSING) Button(onClick=vm::downloadModel,modifier=Modifier.fillMaxWidth()) {Text("Download ${vm.selectedModel.shortName} · ${vm.selectedModel.size}")}
    if(!vm.visionInstalled) OutlinedButton(onClick=vm::downloadVision,enabled=vm.modelState in listOf(ModelState.ASLEEP,ModelState.MISSING),modifier=Modifier.fillMaxWidth()) {Text("Download image support · ${vm.selectedModel.visionBytes/1_000_000} MB")}
    if(vm.downloading) TextButton(onClick=vm::cancelDownload,modifier=Modifier.fillMaxWidth()) {Text("Pause download")}
    TextButton(onClick=onImport,enabled=vm.modelState in listOf(ModelState.ASLEEP,ModelState.MISSING),modifier=Modifier.fillMaxWidth()) {Text("Import model file instead")}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Settings(vm:BonsaiViewModel,onModels:()->Unit,onMemory:()->Unit,onImport:()->Unit,onVisionImport:()->Unit) {
    val c=MaterialTheme.colorScheme
    Text("Settings",style=MaterialTheme.typography.headlineSmall)
    TextButton(onClick=onMemory,modifier=Modifier.fillMaxWidth().padding(top=12.dp)) {Icon(Icons.Outlined.BookmarkBorder,null,Modifier.size(18.dp));Text("Memory · ${vm.memories.size} saved",Modifier.padding(start=12.dp).weight(1f));Icon(Icons.Outlined.ChevronRight,null)}
    Spacer(Modifier.height(22.dp)); Micro("APPEARANCE")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top=12.dp,bottom=24.dp)) {listOf("System","Light","Dark").forEachIndexed {i,label->SegmentedButton(selected=vm.theme==i,onClick={vm.updateTheme(i)},shape=SegmentedButtonDefaults.itemShape(i,3)) {Text(label)}}}
    Micro("MODEL")
    TextButton(onClick=onModels,modifier=Modifier.fillMaxWidth()) {Text(vm.selectedModel.displayName,Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null)}
    if(vm.modelState==ModelState.READY) OutlinedButton(onClick=vm::unload,enabled=!vm.generating,modifier=Modifier.fillMaxWidth()) {Text("Unload model")}
    Text(if(vm.visionInstalled) "Image support installed. Vision loads only for image requests." else "Image support not installed for this model.",color=c.onSurfaceVariant,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=12.dp,bottom=24.dp))
    Micro("RESPONSE LENGTH")
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf(256 to "Short",512 to "Balanced",1024 to "Long").forEach {(n,label)->FilterChip(selected=vm.responseLimit==n,onClick={vm.setLimit(n)},enabled=!vm.generating,label={Text(label)})}}
    Text("Thinking off · 4,096-token context. Older turns leave active context as the chat grows.",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,modifier=Modifier.padding(top=4.dp,bottom=24.dp))
    Micro("PROCESSING")
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf(2,4,6).forEach {n->FilterChip(selected=vm.threads==n,onClick={vm.updateThreads(n)},enabled=vm.modelState==ModelState.ASLEEP || vm.modelState==ModelState.MISSING,label={Text("$n threads")})}}
    Text("4 threads recommended. Unload the model before changing.",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,modifier=Modifier.padding(top=4.dp,bottom=22.dp))
    OutlinedButton(onClick=onImport,enabled=vm.modelState==ModelState.ASLEEP || vm.modelState==ModelState.MISSING,modifier=Modifier.fillMaxWidth()) {Text("Import ${vm.selectedModel.shortName} model file")}
    TextButton(onClick=onVisionImport,enabled=vm.modelState==ModelState.ASLEEP || vm.modelState==ModelState.MISSING,modifier=Modifier.fillMaxWidth()) {Text("Import ${vm.selectedModel.shortName} vision file")}
    HorizontalDivider(Modifier.padding(vertical=22.dp),color=c.outlineVariant)
    Text("EMBER ${BuildConfig.VERSION_NAME}\nBonsai models by PrismML · Based on Qwen\nModels licensed under Apache 2.0\nAI can make mistakes. Check important answers.",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
}
