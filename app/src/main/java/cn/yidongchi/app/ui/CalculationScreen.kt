package cn.yidongchi.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yidongchi.app.*
import cn.yidongchi.core.*
import java.math.BigDecimal
import java.time.LocalDate

@Composable fun CalculationScreen(state:AppState) {
    val context=LocalContext.current
    val s=state.snapshots[state.selected]
    var nextDay by remember {mutableStateOf(false)}
    var indexChange by remember(state.selected){mutableStateOf(state.store.value("index-${state.selected}","0"))}
    var expanded by remember {mutableStateOf<PriceLevel?>(null)}
    var resetEditor by remember {mutableStateOf(false)}
    var replayDate by remember(state.selected){mutableStateOf("")}
    val snapshot=if(replayDate.isBlank())s else state.historySnapshot(replayDate)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        SearchBox(state,{state.select(it);replayDate=""})
        if(snapshot==null){Panel {Text("正在准备计算");Note(state.errors[state.selected]?:"首次打开需要获取个股与基准指数日线。");Button(onClick={state.refreshNow()}){Text("重新获取")}}}
        else {
            SnapshotHeader(state,snapshot)
            val change=indexChange.toBigDecimalOrNull()?:BigDecimal.ZERO
            val outcome=runCatching {if(nextDay)Projection.project(snapshot.quote.stock,snapshot.bars,snapshot.indexBars,Scenario(dailyIndexPercent=change),1,state.resets(state.selected)).first().analysis else state.analysis(snapshot,change)}
            val a=outcome.getOrNull()
            Panel {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(!nextDay,{nextDay=false},label={Text("当日收盘假设")});FilterChip(nextDay,{nextDay=true},label={Text("下一交易日")})}
                OutlinedTextField(indexChange,{indexChange=it;state.store.put("index-${state.selected}",it)},label={Text(if(nextDay)"指数较最近点位变化（%）" else "指数较最新点位变化（%）")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                Note("基准 ${snapshot.quote.stock.board.indexSymbol} · 最新 ${snapshot.indexQuote.price.money()}；变化假设在此基础上计算。")
                Note("${a?.targetDate?:snapshot.quote.time.toLocalDate()} · 指数假设 ${a?.indexPrice?.money()?:"—"}")
                outcome.exceptionOrNull()?.let {Note(it.message?:"当前条件无法计算",Amber)}
            }
            if(a!=null)Section("异动临界价","点选一项查看区间和取整过程。价格指标以收盘为准。") {
                if(a.levels.isEmpty())Panel {Text("暂无适用的价格窗口");Note("可能为无涨跌幅限制日，或历史与指数日期不齐。",Amber)}
                a.levels.groupBy {it.ruleId}.forEach {(_,levels)->Panel {
                    Text(levels.first().label,fontWeight=FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){levels.forEach {l->Column(Modifier.weight(1f).clickable {expanded=l}) {
                        Note(if(l.direction==Direction.UP)"上行触发 ≥" else "下行触发 ≤")
                        Text(l.price.money(),fontSize=if(state.large)28.sp else 24.sp,fontWeight=FontWeight.Bold,color=if(l.direction==Direction.UP)Up else Down)
                        Note("较情景昨收 ${l.price.ratio(a.previousClose).subtract(BigDecimal.ONE).pct()}")
                        Note(when {l.satisfied->"若按情景价收盘满足";!l.reachable->"该指数假设下当日不可达";else->"距情景价 ${l.price.subtract(a.current).money()}"},if(l.satisfied)Up else MaterialTheme.colorScheme.onSurfaceVariant)
                    }}}
                    Note("区间偏离 ${levels.first().deviation.pct()} · ${levels.first().sessions} 个有效记录")
                };Spacer(Modifier.height(8.dp))}
                TextButton(onClick={copyText(context,"${snapshot.quote.stock.name} ${snapshot.quote.stock.code}\n目标 ${a.targetDate}；指数 ${snapshot.quote.stock.board.indexSymbol} 假设 ${a.indexPrice}\n"+a.levels.joinToString("\n"){"${it.label}${if(it.direction==Direction.UP)" ≥ " else " ≤ "}${it.price}"}+"\n数据 ${snapshot.quote.time}；条件估算，公告重置与除权信息待核对。");state.message="已复制计算摘要"}){Text("复制计算摘要")}
            }
            Panel {Text("计算依据",fontWeight=FontWeight.SemiBold);Note(snapshot.notes,Amber);Note("价格范围按板块标准涨跌幅估算；无涨跌幅限制和特殊状态须核对公告。不得据此认定一定停牌或监管安全。");TextButton(onClick={resetEditor=true}){Text("核对 / 校准公告重置起算日")}}
            val dates=state.store.savedDates(state.selected)
            if(dates.isNotEmpty())Section("历史快照回放","仅展示本机已采集快照；正式公告采用当前本地记录，事件知识截止不完整。") {
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(replayDate.isBlank(),{replayDate=""},label={Text("最新")});dates.forEach {d->FilterChip(replayDate==d,{replayDate=d},label={Text(d)})}}
            }
        }
        Spacer(Modifier.height(20.dp))
    }
    expanded?.let {l->AlertDialog(onDismissRequest={expanded=null},title={Text(l.label)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("${l.start} 至 ${l.end} · ${l.sessions} 条记录");Text("区间偏离 = 个股区间收益 − 基准指数区间收益");Text("门槛 ${l.threshold.pct()}；当前条件值 ${l.deviation.pct()}");Text("理论反解价 ${l.theoretical.stripTrailingZeros().toPlainString()}");Text("最小报价单位 ${snapshot?.quote?.stock?.tick} → ${l.price}");Note("上行取首个满足门槛的报价，下行取最后一个满足门槛的报价。正向比较不使用显示舍入值。");Note("累计公式的科创板适用关系仍有解释待补证，结果为条件估算。",Amber)}},confirmButton={TextButton(onClick={expanded=null}){Text("知道了")}})}
    if(resetEditor){val reset=state.resets(state.selected);var normal by remember{mutableStateOf(reset.normal?.toString()?:"")};var severe by remember{mutableStateOf(reset.severe?.toString()?:"")};var error by remember{mutableStateOf("")}
        AlertDialog(onDismissRequest={resetEditor=false},title={Text("公告重置起算日")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Note("填写按原公告核实后的首个重新计数交易日；不是简单填写公告日期。留空表示未知。",Amber);OutlinedTextField(normal,{normal=it},label={Text("普通异动 YYYY-MM-DD")});OutlinedTextField(severe,{severe=it},label={Text("严重异动 YYYY-MM-DD")});if(error.isNotBlank())Note(error,Up)}},confirmButton={TextButton(onClick={try{state.setResets(normal,severe);resetEditor=false}catch(e:Exception){error="请使用 YYYY-MM-DD 日期格式"}}){Text("保存条件")}},dismissButton={TextButton(onClick={resetEditor=false}){Text("取消")}})
    }
}
