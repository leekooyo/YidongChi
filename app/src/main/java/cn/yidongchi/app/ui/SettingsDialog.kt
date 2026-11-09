package cn.yidongchi.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.yidongchi.app.*

@Composable fun SettingsDialog(state:AppState,onDismiss:()->Unit) {
    val context=LocalContext.current
    var pending by remember {mutableStateOf<String?>(null)}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null){context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {it.write(state.store.backup())};state.message="自选备份已导出"}}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)try {val text=context.contentResolver.openInputStream(uri)!!.bufferedReader().use {it.readText()};BackupCodec.decode(text);pending=text}catch(e:Exception){state.message="备份无法读取：${e.message}"}}
    AlertDialog(onDismissRequest=onDismiss,title={Text("偏好与工具")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("外观")
        Row {listOf("system" to "系统","light" to "浅色","dark" to "深色").forEach {(k,t)->FilterChip(state.theme==k,{state.setting("theme",k)},label={Text(t)})}}
        Row {Checkbox(state.large,{state.setting("large",it.toString())});Text("更大的价格数字",Modifier.padding(top=12.dp))}
        OutlinedTextField(state.threshold,{v->if(v.isBlank()||v.toBigDecimalOrNull()?.let {it>=java.math.BigDecimal.ZERO&&it<="100".toBigDecimal()}==true)state.setting("threshold",v)},label={Text("临界价接近提醒（占现价 %）")},singleLine=true)
        Note("提醒仅在 APP 前台刷新时生成。可在单股管理中静音。")
        HorizontalDivider()
        TextButton(onClick={export.launch("异动尺自选备份.json")}){Text("导出自选 / 设置备份")}
        TextButton(onClick={import.launch(arrayOf("application/json","text/plain"))}){Text("导入并合并备份")}
        TextButton(onClick={state.undoImport()}){Text("恢复到导入前")}
        TextButton(onClick={state.clearHistory();state.message="最近搜索已清除"}){Text("清除最近搜索")}
        HorizontalDivider()
        Text("快速说明")
        Note("临界价：给定指数点位和起算条件下，刚好满足某个价格指标的报价。\n偏离：个股区间收益减基准指数区间收益。\n3/10/30日：交易日观察窗口，并非自然日。\n监控期间：特定事件后的监管规定，不等于股票禁止交易。\n条件估算：重置、除权或证券历史状态尚未完整核实。")
        Note("异动尺 0.1.0 · 本地保存，无账号。规则核验截至 2026-11-09；2026交易日历之后需更新。")
    }},confirmButton={TextButton(onClick=onDismiss){Text("完成")}})
    pending?.let {text->val data=BackupCodec.decode(text);AlertDialog(onDismissRequest={pending=null},title={Text("合并备份")},text={Text("识别到 ${data.items.size} 只自选；现有股票分组优先保留。将保存导入前恢复副本。")},confirmButton={TextButton(onClick={state.import(text);pending=null}){Text("合并")}},dismissButton={TextButton(onClick={pending=null}){Text("取消")}})}
}
