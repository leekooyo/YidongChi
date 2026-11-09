package cn.yidongchi.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yidongchi.app.*
import cn.yidongchi.core.*

@Composable fun WatchScreen(state:AppState,onOpen:(String)->Unit) {
    var editing by remember {mutableStateOf<WatchItem?>(null)}
    val groups=listOf("全部")+state.watches.map {it.group}.distinct()
    val list=state.watches.filter {state.group=="全部"||it.group==state.group}.let {items->when(state.sort){"名称"->items.sortedBy {it.name};"临界距离"->items.sortedBy {w->state.snapshots[w.symbol]?.let {s->state.analysis(s).levels.minOfOrNull {it.price.subtract(s.quote.price).abs().ratio(s.quote.price)}}};else->items.sortedByDescending {it.pinned}}}.filter {w->when(state.filter){"临界附近"->state.snapshots[w.symbol]?.let {s->state.analysis(s).levels.any {it.reachable&&it.price.subtract(s.quote.price).abs().ratio(s.quote.price)<".03".bd()}}?:false;"近期异动"->state.snapshots[w.symbol]?.let {state.recent(it).isNotEmpty()}?:false;"数据待核实"->true;else->true}}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp).testTag("watchlist"),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item {Section("我的自选 · ${state.watches.size}") {SearchBox(state,onOpen)}}
        item {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){groups.forEach {g->FilterChip(state.group==g,{state.group=g;state.store.put("group",g)},label={Text(g)})}}}
        item {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("全部","临界附近","近期异动","数据待核实").forEach {f->FilterChip(state.filter==f,{state.filter=f},label={Text(f)})};TextButton(onClick={state.sort=when(state.sort){"置顶"->"名称";"名称"->"临界距离";else->"置顶"};state.store.put("sort",state.sort)}){Text("排序：${state.sort}")}}}
        if(state.filter=="数据待核实")item {Pill("待核实",Amber)}
        if(list.isEmpty())item {Panel {Icon(Icons.Outlined.StarOutline,null);Text(if(state.watches.isEmpty())"先关注一只股票" else "当前筛选下没有股票",fontSize=20.sp,fontWeight=FontWeight.Bold);OutlinedButton(onClick={onOpen("sh600000")}){Text("查看浦发银行")}}}
        items(list,key={it.symbol}) {w->
            val s=state.snapshots[w.symbol]
            Panel(Modifier.clickable {onOpen(w.symbol)}) {
                Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(s?.quote?.stock?.name?:w.name,fontWeight=FontWeight.Bold,fontSize=18.sp);Note("${w.symbol.drop(2)} · ${w.group}${if(w.pinned)" · 置顶" else ""}")};if(s!=null)Text(s.quote.price.money(),fontWeight=FontWeight.Bold,fontSize=22.sp);IconButton(onClick={editing=w}){Icon(Icons.Outlined.MoreHoriz,"管理自选")}}
                if(s!=null){val a=state.analysis(s)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        listOf("severe10" to "10日严重异动","severe30" to "30日严重异动").forEach{(rule,label)->Column(Modifier.weight(1f)) {
                            Text(label,fontSize=14.sp)
                            val up=a.levels.firstOrNull{it.ruleId==rule&&it.direction==Direction.UP}
                            val down=a.levels.firstOrNull{it.ruleId==rule&&it.direction==Direction.DOWN}
                            Text("↑ ${up?.price?.money()?:"—"}",color=Up,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                            Text("↓ ${down?.price?.money()?:"—"}",color=Down,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                        }}
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Note("${s.quote.time.toLocalTime()}${if(w.muted)" · 静音"else ""}")
                        Pill(if(state.recent(s).isEmpty())"估算"else "近期记录 ${state.recent(s).size}",Amber)
                    }
                }else Note(state.errors[w.symbol]?:"等待刷新行情",Amber)
            }
        }
    }
    editing?.let {w->var group by remember(w.symbol){mutableStateOf(w.group)}
        AlertDialog(onDismissRequest={editing=null},title={Text("管理 ${w.name}")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(group,{group=it},label={Text("分组")});TextButton(onClick={state.update(w.copy(group=group.ifBlank {"默认"},pinned=!w.pinned));editing=null}){Text(if(w.pinned)"取消置顶" else "置顶")};TextButton(onClick={state.update(w.copy(group=group.ifBlank {"默认"},muted=!w.muted));editing=null}){Text(if(w.muted)"开启提醒" else "静音此股")};Row{TextButton(onClick={state.move(w.symbol,-1)}){Text("上移")};TextButton(onClick={state.move(w.symbol,1)}){Text("下移")}};TextButton(onClick={state.remove(w.symbol);editing=null}){Text("移出自选",color=Up)}}},confirmButton={TextButton(onClick={state.update(w.copy(group=group.ifBlank {"默认"}));editing=null}){Text("保存")}},dismissButton={TextButton(onClick={editing=null}){Text("取消")}})
    }
}
