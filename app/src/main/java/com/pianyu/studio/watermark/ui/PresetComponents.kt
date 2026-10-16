package com.pianyu.studio.watermark.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pianyu.studio.watermark.ui.theme.*

@Composable
fun PresetNameDialog(title: String, initialName: String, cancel: () -> Unit, save: (String) -> Unit) {
    var name by rememberSaveable(initialName) {mutableStateOf(initialName)}
    GlassDialog(onDismissRequest=cancel,title={Text(title)},text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value=name,onValueChange={if(it.length<=40) name=it},label={Text("预设名称")},singleLine=true,modifier=Modifier.fillMaxWidth(),
                colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Ice,focusedLabelColor=Ice,cursorColor=Ice))
            Text("保存签名、位置、大小、颜色、透明度和导出设置。",color=Muted)
        }
    },confirmButton={TextButton(onClick={save(name.trim())},enabled=name.isNotBlank()) {Text("保存")}},
        dismissButton={TextButton(onClick=cancel) {Text("取消")}})
}
