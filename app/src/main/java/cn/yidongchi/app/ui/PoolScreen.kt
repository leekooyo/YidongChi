package cn.yidongchi.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable fun WatchHome(state:AppState,onOpen:(String)->Unit,onPoolOpen:(String)->Unit) {
    var pool by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(!pool,{pool=false},label={Text("我的自选")})
            FilterChip(pool,{pool=true},label={Text("明日池")})
        }
        Box(Modifier.weight(1f)){if(pool)PoolScreen(state,onPoolOpen)else WatchScreen(state,onOpen)}
    }
}

@Composable fun PoolScreen(state:AppState,onOpen:(String)->Unit) {
    val batch=state.poolBatch
    var direction by remember{mutableStateOf(Direction.UP)}
    var rule by remember{mutableStateOf<String?>(null)}
    var onlyWatches by remember{mutableStateOf(false)}
    var ascending by remember{mutableStateOf(true)}
    var more by remember{mutableStateOf(false)}
    var details by remember{mutableStateOf(false)}
    var expanded by remember{mutableStateOf<String?>(null)}
    val rows=remember(batch,direction,rule,onlyWatches,state.watches,ascending) {
        TomorrowPool.filtered(batch?.rows?:emptyList(),direction,rule,if(onlyWatches)state.watches.map{it.symbol}.toSet()else null).let{if(ascending)it else it.reversed()}
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp).testTag("pool-list"),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item {Panel {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("明日可能异动",Modifier.weight(1f),fontSize=21.sp,fontWeight=FontWeight.Bold)
                Pill("条件估算",Amber)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("全部沪深 A 股")
                if(batch!=null)Text("${TradingCalendar.nextDays(batch.basis.time.toLocalDate(),1).first().toString().drop(5)} · 指数不变")
            }
            if(batch!=null)Text("收盘 ${batch.basis.time.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))} · ${if(batch.complete)"完整结果"else "未完成"}",color=MaterialTheme.colorScheme.onSurfaceVariant)
            val progress=state.poolProgress
            val working=progress.batch
            if(progress.stage.isNotBlank())Text("${progress.stage}${if(state.poolRunning&&working!=null)" ${working.processed}/${working.universe.size}"else ""}",color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(state.poolRunning) {
                if(working==null)LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress={working.processed.toFloat()/working.universe.size.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth())
            }
            if(state.poolError.isNotBlank())Text(state.poolError,color=Amber)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                Button(onClick={if(state.poolRunning)state.pausePool()else state.startPool()},enabled=!state.poolLoading){Text(if(state.poolRunning)"暂停更新"else if(state.poolLoading)"导入已有缓存"else "更新收盘数据")}
                if(batch!=null)TextButton(onClick={details=true}) {Text("命中 ${batch.rows.size} · 详情")}
                Box {
                    TextButton(onClick={more=true},enabled=!state.poolRunning&&!state.poolLoading){Text("更多")}
                    DropdownMenu(expanded=more,onDismissRequest={more=false}) {
                        DropdownMenuItem(text={Text("强制同步本日")},onClick={more=false;state.startPool(newBatch=true)})
                        DropdownMenuItem(text={Text("重建历史库")},onClick={more=false;state.startPool(newBatch=true,rebuild=true)})
                    }
                }
            }

        }}
        item {Column {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(direction==Direction.UP,{direction=Direction.UP},label={Text("上行")})
                FilterChip(direction==Direction.DOWN,{direction=Direction.DOWN},label={Text("下行")})
                FilterChip(onlyWatches,{onlyWatches=!onlyWatches},label={Text("只看自选")})
            }
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(null to "全部类型","severe10" to "10日","severe30" to "30日").forEach{(key,label)->FilterChip(rule==key,{rule=key},label={Text(label)})}
                TextButton(onClick={ascending=!ascending}){Text("所需幅度 ${if(ascending)"↑"else "↓"}")}
            }
        }}
        if(rows.isEmpty())item{Panel{Text(when{batch==null->"收盘后更新，查看明日候选";state.poolRunning->"更新中，继续显示上次结果";!batch.complete->"更新尚未完成";else->"当前筛选下没有股票"},fontSize=17.sp)}}
        items(rows,key={it.stock.symbol}){row->
            val hit=row.hits.first();val color=if(direction==Direction.UP)Up else Down
            val watched=state.watches.any{it.symbol==row.stock.symbol}
            Panel(Modifier.clickable{onOpen(row.stock.symbol)}.testTag("pool-${row.stock.symbol}")) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)){Text(row.stock.name,fontWeight=FontWeight.Bold,fontSize=19.sp);Text(row.stock.code,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Pill(if(hit.level.ruleId=="severe10")"10日"else "30日",color)
                    IconButton(onClick={if(watched)state.remove(row.stock.symbol)else state.add(row.stock)}){Icon(if(watched)Icons.Outlined.Star else Icons.Outlined.StarOutline,if(watched)"移出自选"else "加入自选")}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Column{Text("现价");Text(row.price.money(),fontSize=22.sp)}
                    Column{Text("触发价");Text(hit.level.price.money(),fontSize=24.sp,fontWeight=FontWeight.Bold,color=color)}
                    Column(horizontalAlignment=Alignment.End){Text("所需涨跌幅");Text(if(hit.requiredChange.signum()==0)"零涨跌已达"else hit.requiredChange.pct(),fontSize=20.sp,fontWeight=FontWeight.Bold,color=color)}
                }
                if(row.hits.size>1) {
                    TextButton(onClick={expanded=if(expanded==row.stock.symbol)null else row.stock.symbol},contentPadding=PaddingValues(0.dp)){Text(if(expanded==row.stock.symbol)"收起其他条件"else "另有 ${row.hits.size-1} 项条件")}
                    if(expanded==row.stock.symbol)row.hits.drop(1).forEach{h->Text("${if(h.level.ruleId=="severe10")"10日"else "30日"} · ${h.level.price.money()} · ${if(h.requiredChange.signum()==0)"零涨跌已达"else h.requiredChange.pct()}",color=color)}
                }
            }
        }
    }
    if(details&&batch!=null)AlertDialog(onDismissRequest={details=false},title={Text("更新详情")},text={
        LazyColumn(Modifier.heightIn(max=400.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item{Text("本次请求 ${state.poolProgress.requests} · 历史回补 ${state.poolProgress.repairs}\n失败 ${state.poolProgress.batch?.failures?.size?:batch.failures.size} · 排除 ${batch.exclusions.size}")}
            item{Text("${(batch.outcomes.values.mapNotNull{it.provider}+batch.basis.provider).distinct().joinToString("\n")}\n假设指数不变；公告重置与特殊状态未全面核实。\n排除和失败不等于没有异动。")}
            val detailBatch=state.poolProgress.batch?:batch
            val issues=detailBatch.outcomes.filterValues{it.issue!=null}
            items(detailBatch.universe.filter{it.symbol in issues},key={it.symbol}){s->Text("${s.name} ${s.code} · ${if(issues.getValue(s.symbol).excluded)"排除"else "失败"}\n${issues.getValue(s.symbol).issue}")}
        }
    },confirmButton={TextButton(onClick={details=false}){Text("关闭")}})
}
