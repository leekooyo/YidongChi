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
    var details by remember{mutableStateOf(false)}
    var expanded by remember{mutableStateOf<String?>(null)}
    var confirmRescan by remember{mutableStateOf(false)}
    val rows=remember(batch,direction,rule,onlyWatches,state.watches,ascending) {
        TomorrowPool.filtered(batch?.rows?:emptyList(),direction,rule,if(onlyWatches)state.watches.map{it.symbol}.toSet()else null).let{if(ascending)it else it.reversed()}
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp).testTag("pool-list"),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item {Panel {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("明日可能异动",Modifier.weight(1f),fontSize=21.sp,fontWeight=FontWeight.Bold)
                Pill("条件估算",Amber)
            }
            if(batch==null)Text("全部沪深 A 股") else {
                val target=TradingCalendar.nextDays(batch.basis.time.toLocalDate(),1).first()
                Text("${target.toString().drop(5)} · 指数不变",fontSize=18.sp)
                val intraday=batch.basis.time.toLocalTime()<LocalTime.of(15,0)
                Text("基准 ${batch.basis.time.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))}${if(intraday)" · 盘中估算"else ""}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                val label=when{state.poolRunning->"扫描中";batch.complete->"扫描完成";else->"未完成"}
                Text("$label ${batch.processed} / ${batch.universe.size}")
                if(state.poolRunning)LinearProgressIndicator(progress={batch.processed.toFloat()/batch.universe.size.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth())
                TextButton(onClick={details=true},contentPadding=PaddingValues(0.dp)) {Text("命中 ${batch.rows.size} · 失败 ${batch.failures.size} · 排除 ${batch.exclusions.size}")}
            }
            if(state.poolError.isNotBlank())Text(state.poolError,color=Amber)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if(state.poolRunning) {
                    OutlinedButton(onClick={state.pausePool()}){Text("暂停扫描")}
                    if(batch==null)Text("正在读取全市场清单",Modifier.align(Alignment.CenterVertically))
                }else {
                    Button(onClick={if(batch?.complete==true)confirmRescan=true else state.startPool()},enabled=!state.poolLoading){Text(when{state.poolLoading->"读取上次记录";batch==null->"开始扫描";batch.complete->"重新扫描";else->"继续扫描"})}
                    if(batch!=null&&batch.failures.isNotEmpty())OutlinedButton(onClick={state.startPool(retry=true)}){Text("重试失败项")}
                    if(batch!=null&&!batch.complete)TextButton(onClick={confirmRescan=true}){Text("重扫")}
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
        if(rows.isEmpty())item{Panel{Text(when{batch==null->"扫描后查看明日异动候选";state.poolRunning->"已扫描范围暂无符合条件的股票";!batch.complete->"当前已扫描范围暂无结果";else->"当前筛选下没有股票"},fontSize=17.sp)}}
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
    if(confirmRescan)AlertDialog(onDismissRequest={confirmRescan=false},title={Text("重新扫描全市场？")},text={Text("使用最新行情重新建立股票池，替换上次结果。日线缓存继续复用。")},confirmButton={TextButton(onClick={confirmRescan=false;state.startPool(newBatch=true)}){Text("重新扫描")}},dismissButton={TextButton(onClick={confirmRescan=false}){Text("取消")}})
    if(details&&batch!=null)AlertDialog(onDismissRequest={details=false},title={Text("扫描详情")},text={
        LazyColumn(Modifier.heightIn(max=400.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item{Text("${(batch.outcomes.values.mapNotNull{it.provider}+batch.basis.provider).distinct().joinToString("\n")}\n假设指数不变；公告重置与特殊状态未全面核实。\n排除和失败不等于没有异动。")}
            val issues=batch.outcomes.filterValues{it.issue!=null}
            items(batch.universe.filter{it.symbol in issues},key={it.symbol}){s->Text("${s.name} ${s.code} · ${if(issues.getValue(s.symbol).excluded)"排除"else "失败"}\n${issues.getValue(s.symbol).issue}")}
        }
    },confirmButton={TextButton(onClick={details=false}){Text("关闭")}})
}
