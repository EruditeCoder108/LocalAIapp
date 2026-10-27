package dev.bonsai.pocket

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun MemorySheet(vm: BonsaiViewModel) {
    var draft by remember {mutableStateOf("")}
    var pin by remember {mutableStateOf(false)}
    var deleting by remember {mutableStateOf<MemoryFact?>(null)}
    Text("Memory",style=MaterialTheme.typography.headlineSmall)
    Text("Small facts you choose to keep between chats. Pin up to three facts to always include; other facts are matched by words in your question.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=10.dp,bottom=14.dp))
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text("Use saved memory",Modifier.weight(1f));Switch(checked=vm.memoryEnabled,onCheckedChange=vm::enableMemory)
    }
    OutlinedTextField(draft,{if(it.length<=500) draft=it},label={Text("Add a fact or preference")},placeholder={Text("I prefer short explanations")},modifier=Modifier.fillMaxWidth().padding(top=14.dp),maxLines=4)
    Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(pin,{pin=it});Text("Always include",style=MaterialTheme.typography.bodySmall)}
    Button(onClick={vm.addMemory(draft,pin);draft="";pin=false},enabled=draft.isNotBlank() && vm.memoryReady && !vm.memoryBusy,modifier=Modifier.fillMaxWidth()) {Text("Save memory")}
    Text("You can also type “Remember that …” in a chat. Nothing is extracted from your chats automatically.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=18.dp))
    vm.memories.forEach {fact->
        Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainerHigh,modifier=Modifier.fillMaxWidth().padding(bottom=10.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text(fact.text,style=MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    IconButton(onClick={vm.pinMemory(fact.id,!fact.pinned)},enabled=!vm.memoryBusy) {Icon(Icons.Outlined.PushPin,if(fact.pinned) "Unpin memory" else "Pin memory",tint=if(fact.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
                    IconButton(onClick={deleting=fact},enabled=!vm.memoryBusy) {Icon(Icons.Outlined.Close,"Remove memory")}
                }
            }
        }
    }
    if(vm.memories.isEmpty()) Text("No saved memories yet.",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=20.dp))
    deleting?.let {fact->AlertDialog(onDismissRequest={deleting=null},title={Text("Remove this memory?")},text={Text(fact.text)},confirmButton={TextButton(onClick={vm.removeMemory(fact.id);deleting=null}) {Text("Remove")}},dismissButton={TextButton(onClick={deleting=null}) {Text("Cancel")}})}
}
