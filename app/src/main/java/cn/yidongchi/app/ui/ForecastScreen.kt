package cn.yidongchi.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yidongchi.app.*
import cn.yidongchi.core.*
import java.math.BigDecimal

@Composable fun ForecastScreen(state:AppState) {
    val s=state.snapshots[state.selected]
    var stockChange by remember(state.selected){mutableStateOf(state.store.value("scenario-stock-${state.selected}","0"))}
    var indexChange by remember(state.selected){mutableStateOf(state.store.value("scenario-index-${state.selected}","0"))}
    var pathText by remember {mutableStateOf("")}
    var indexPath by remember {mutableStateOf("")}
    var mode by remember {mutableStateOf("固定涨跌")}
    var reset by remember {mutableStateOf(false)}
    var compare by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Section("未来 10 个交易日","每一天重新滚动窗口；这是条件推演，不是价格预测。"){}
        if(s==null){Panel {Text("先在计算页选择股票")};return@Column}
        SnapshotHeader(state,s)
        Panel {
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("固定涨跌","连续涨停","连续跌停","逐日路径").forEach {m->FilterChip(mode==m,{mode=m},label={Text(m)})}}
            if(mode=="固定涨跌")OutlinedTextField(stockChange,{stockChange=it;state.store.put("scenario-stock-${state.selected}",it)},Modifier.fillMaxWidth(),label={Text("个股每日涨跌幅（%）")},singleLine=true)
            OutlinedTextField(indexChange,{indexChange=it;state.store.put("scenario-index-${state.selected}",it)},Modifier.fillMaxWidth(),label={Text("基准指数每日涨跌幅（%）")},singleLine=true)
            if(mode=="逐日路径"){OutlinedTextField(pathText,{pathText=it},Modifier.fillMaxWidth(),label={Text("个股路径，如 2,3,-1,0")});OutlinedTextField(indexPath,{indexPath=it},Modifier.fillMaxWidth(),label={Text("指数路径，如 0.2,-0.1,0")});Note("逗号分隔每日百分数，未填写的后续日按上面的固定值。")}
            Row {Checkbox(reset,{reset=it});Text("模拟触发后次交易日重置",Modifier.padding(top=12.dp),fontSize=13.sp)}
            Row {Checkbox(compare,{compare=it});Text("与价格 / 指数均不变的情景 B 对照",Modifier.padding(top=12.dp),fontSize=13.sp)}
        }
        val result=runCatching {
            fun path(t:String)=if(t.isBlank())emptyList() else t.replace('，',',').split(',').map {it.trim().bd()}
            val scenario=Scenario("情景A",stockChange.bd(),indexChange.bd(),if(mode=="逐日路径")path(pathText)else emptyList(),if(mode=="逐日路径")path(indexPath)else emptyList(),when(mode){"连续涨停"->1;"连续跌停"->-1;else->0},reset)
            Projection.project(s.quote.stock,s.bars,s.indexBars,scenario,resets=state.resets(state.selected),events=state.events.filter{it.symbol==state.selected})
        }
        val days=result.getOrNull()
        if(days==null)Panel {Note(result.exceptionOrNull()?.message?:"请输入有效涨跌幅",Amber)}
        else {
            val first=days.firstOrNull {it.analysis.levels.any {l->l.ruleId!="normal"&&l.satisfied}||it.directions.triggered}
            Panel {Pill(if(first!=null)"情景下出现严重异动条件" else "本路径内未出现严重异动条件",if(first!=null)Up else Accent);Text(first?.let {"首次：${it.date}"}?:"未来 10 日",fontSize=22.sp,fontWeight=FontWeight.Bold);Note("未满足条件不等于未来不会触发；依赖股价路径、指数路径和重置假设。",Amber)
                Canvas(Modifier.fillMaxWidth().height(90.dp)){val values=days.map {it.close.toFloat()};val low=values.min();val span=(values.max()-low).coerceAtLeast(.01f);val p=Path();values.forEachIndexed{i,v->val x=i*size.width/(values.size-1);val y=size.height-(v-low)/span*(size.height-8);if(i==0)p.moveTo(x,y) else p.lineTo(x,y)};drawPath(p,Accent,style=androidx.compose.ui.graphics.drawscope.Stroke(4f))}
            }
            val b=if(compare)runCatching {Projection.project(s.quote.stock,s.bars,s.indexBars,Scenario("情景B"),resets=state.resets(state.selected))}.getOrNull()else null
            days.forEachIndexed {i,d->Panel {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("D${i+1} · ${d.date}",fontWeight=FontWeight.Bold);Text("情景收盘 ${d.close.money()}",fontWeight=FontWeight.Bold)}
                Note("指数假设 ${d.indexClose.money()} · 情景昨收 ${d.analysis.previousClose.money()}")
                if(d.analysis.levels.isEmpty())Note("该目标日无适用价格窗口（如上市前五日豁免）",Amber)
                d.analysis.levels.groupBy {it.ruleId}.forEach {(_,ls)->val up=ls.firstOrNull {it.direction==Direction.UP};val down=ls.firstOrNull {it.direction==Direction.DOWN};Text("${ls.first().label}  ↑ ${up?.price?.money()?:"—"}  ↓ ${down?.price?.money()?:"—"}",fontSize=14.sp)
                    Note("条件偏离 ${ls.first().deviation.pct()} · 上行${if(up?.reachable==true)"当日可达" else "当日不可达"} / 下行${if(down?.reachable==true)"当日可达" else "当日不可达"}")
                    val previous=if(i==0)state.analysis(s)else days[i-1].analysis
                    val old=previous.levels.firstOrNull{it.ruleId==ls.first().ruleId&&it.direction==Direction.UP}
                    if(old!=null&&up!=null&&old.start!=up.start)Note("窗口起点 ${old.start} → ${up.start}；滚动及移出影响已计入 ${up.price.subtract(old.price).money()} 的临界价变化。")
                    if(ls.any {it.satisfied})Pill("按本路径满足价格条件",Up)}
                Note("同向普通异动：上行 ${d.directions.up}/${d.directions.threshold}、下行 ${d.directions.down}/${d.directions.threshold}${if(reset)"（包含假设重置后的非重叠模拟事件）" else "（仅已确认历史；未来次数需假设重置）"}")
                if(d.directions.triggered)Pill("本事件路径满足同向多次条件",Up)
                if(d.afterHypotheticalTrigger)Note(if(reset)"包含模拟重置；并非真实公告状态" else "已发生模拟触发；后续未纳入未来公告重置",Amber)
                b?.getOrNull(i)?.let {other->Note("情景 B：收盘 ${other.close.money()} · 10日上行 ${other.analysis.levels.firstOrNull {it.ruleId=="severe10"&&it.direction==Direction.UP}?.price?.money()}")}
            }}
        }
        Panel {Note("超过当日可交易范围的固定路径仅是数学情景；涨跌停路径才按板块报价上限逐日推算。未来停复牌、除权和公告未知，未纳入的变化会改变结果。",Amber)}
        Spacer(Modifier.height(24.dp))
    }
}
