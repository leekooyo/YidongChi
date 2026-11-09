package cn.yidongchi.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import cn.yidongchi.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.math.BigDecimal
import java.time.LocalDate

class AppState(app:Application):AndroidViewModel(app) {
    val store=LocalStore(app)
    private val repository=MarketRepository()
    var watches by mutableStateOf(store.watchlist());private set
    var selected by mutableStateOf(store.value("selected","sh600000"));private set
    val snapshots=mutableStateMapOf<String,MarketSnapshot>()
    val errors=mutableStateMapOf<String,String>()
    var suggestions by mutableStateOf(emptyList<Stock>());private set
    var busy by mutableStateOf(false);private set
    var message by mutableStateOf("")
    var events by mutableStateOf(store.events());private set
    var eventStatus by mutableStateOf("尚未加载记录");private set
    var alerts by mutableStateOf(emptyList<AlertItem>());private set
    var theme by mutableStateOf(store.value("theme","system"));private set
    var large by mutableStateOf(store.value("large","false")=="true");private set
    var threshold by mutableStateOf(store.value("threshold","1"));private set
    var searchText by mutableStateOf("")
    var group by mutableStateOf(store.value("group","全部"))
    var sort by mutableStateOf(store.value("sort","置顶"))
    var filter by mutableStateOf("全部")
    var history by mutableStateOf(store.value("recent").split('|').filter {it.isNotBlank()});private set
    var active by mutableStateOf(true)
    private val loading=mutableSetOf<String>()
    private val eventsFetched=mutableSetOf<String>()
    private var searchJob:Job?=null
    init {store.cached(selected)?.let {snapshots[selected]=it};viewModelScope.launch {refresh()}}
    fun setting(key:String,value:String) {store.put(key,value);when(key){"theme"->theme=value;"large"->large=value=="true";"threshold"->threshold=value}}
    fun select(symbol:String) {
        selected=symbol;store.put("selected",symbol)
        history=(listOf(symbol)+history).distinct().take(8);store.put("recent",history.joinToString("|"))
        store.cached(symbol)?.let {snapshots[symbol]=it};viewModelScope.launch {load(symbol);loadEvents()}
    }
    fun search(text:String) {searchText=text;searchJob?.cancel();searchJob=viewModelScope.launch {delay(350);suggestions=if(text.isBlank())emptyList() else try {repository.search(text)} catch(e:Exception){if(e is CancellationException)throw e;emptyList()}}}
    fun add(stock:Stock) {watches=Watchlist.merge(watches,listOf(WatchItem(stock.symbol,stock.name)));store.saveWatchlist(watches);message="已加入自选"}
    fun update(item:WatchItem) {watches=watches.map {if(it.symbol==item.symbol)item else it};store.saveWatchlist(watches)}
    fun remove(symbol:String) {watches=watches.filter {it.symbol!=symbol};store.saveWatchlist(watches)}
    fun move(symbol:String,offset:Int) {val list=watches.toMutableList();val i=list.indexOfFirst {it.symbol==symbol};val j=i+offset;if(i>=0&&j in list.indices){val item=list.removeAt(i);list.add(j,item);watches=list;store.saveWatchlist(watches)}}
    suspend fun refresh() {if(busy)return;busy=true;try {val gate=Semaphore(3);coroutineScope {(listOf(selected)+watches.map {it.symbol}).distinct().map {launch {gate.withPermit {load(it)}}}.joinAll()}} finally {busy=false}}
    fun refreshNow() {viewModelScope.launch {refresh()}}
    private suspend fun load(symbol:String) {
        if(!loading.add(symbol))return
        try {val s=repository.load(symbol);snapshots[symbol]=s;errors.remove(symbol);withContext(Dispatchers.IO){store.snapshot(s)};evaluate(s);if("$symbol-${s.quote.time.toLocalDate()}" !in eventsFetched)loadEvents(symbol)}
        catch(e:Exception) {if(e is CancellationException)throw e;errors[symbol]=e.message?:"行情读取失败，请重试";if(symbol !in snapshots)store.cached(symbol)?.let {snapshots[symbol]=it};if(store.value("alert-data","true")=="true"){val day=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));alerts=Watchlist.mergeAlerts(alerts,listOf(AlertItem("missing-$symbol-$day",symbol,"${symbol.drop(2)} 数据缺失：${errors[symbol]}",day)))}}
        finally {loading.remove(symbol)}
    }
    fun resets(symbol:String)=ResetDates(store.value("normal-$symbol").takeIf {it.isNotBlank()}?.let(LocalDate::parse),store.value("severe-$symbol").takeIf {it.isNotBlank()}?.let(LocalDate::parse))
    fun setResets(normal:String,severe:String) {for(s in listOf(normal,severe))if(s.isNotBlank())LocalDate.parse(s);store.put("normal-$selected",normal);store.put("severe-$selected",severe);message="已保存重置起算日；计算按此条件更新"}
    fun analysis(s:MarketSnapshot,indexChange:BigDecimal=BigDecimal.ZERO,resetOverride:ResetDates?=null)=PriceEngine.analyze(s.quote.stock,s.bars,s.indexBars,s.quote.time.toLocalDate(),s.indexQuote.price.multiply(BigDecimal.ONE.add(indexChange.movePointLeft(2))),resetOverride?:resets(s.quote.stock.symbol))
    private fun evaluate(s:MarketSnapshot) {
        if(watches.firstOrNull {it.symbol==s.quote.stock.symbol}?.muted==true)return
        val now=java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
        val time=now.toLocalTime();val trading=TradingCalendar.isTradingDay(now.toLocalDate())&&((time>=java.time.LocalTime.of(9,30)&&time<=java.time.LocalTime.of(11,30))||(time>=java.time.LocalTime.of(13,0)&&time<=java.time.LocalTime.of(15,0)))
        if(store.value("alert-data","true")=="true"&&trading&&s.quote.time<now.minusMinutes(5))alerts=Watchlist.mergeAlerts(alerts,listOf(AlertItem("stale-${s.quote.stock.symbol}-${now.toLocalDate()}",s.quote.stock.symbol,"${s.quote.stock.name} 行情已过期，最后时间 ${s.quote.time}",now.toLocalDate())))
        if(store.value("alert-price","true")!="true")return
        val a=analysis(s);val t=threshold.toBigDecimalOrNull()?:BigDecimal.ONE
        val hits=a.levels.filter {it.satisfied||it.reachable&&it.price.subtract(a.current).abs().ratio(a.current).movePointRight(2)<=t}
        val incoming=hits.map {val key="${s.quote.stock.symbol}-${it.ruleId}-${it.direction}-${it.end}-${it.start}";AlertItem(key,s.quote.stock.symbol,"${s.quote.stock.name} · ${it.label}${if(it.direction==Direction.UP)"上行" else "下行"}：${if(it.satisfied)"若按当前行情收盘满足条件" else "接近条件临界价"} ${it.price}",it.end,store.value("read-$key")=="true")}
        alerts=Watchlist.mergeAlerts(alerts,incoming)
    }
    fun readAlert(a:AlertItem){store.put("read-${a.key}","true");alerts=alerts.map {if(it.key==a.key)it.copy(read=true) else it}}
    suspend fun loadEvents(symbol:String=selected) {
        val s=snapshots[symbol]?:return
        if(symbol==selected)eventStatus="正在读取行情源记录"
        try {val incoming=repository.eventCandidates(s.quote.stock);events=EventLogic.merge(events,incoming);store.saveEvents(events);eventsFetched+="$symbol-${s.quote.time.toLocalDate()}";if(symbol==selected)eventStatus="行情源候选记录已读取；不代表官方事件完整覆盖"
            if(store.value("alert-event","true")=="true"&&watches.firstOrNull{it.symbol==symbol}?.muted!=true){val hits=s.recent(events).filter {it.confirmed&&it.kind!="normal"}.map {AlertItem("event-${it.symbol}-${it.date}-${it.kind}",symbol,"${s.quote.stock.name} 存在近期已确认严重异动记录 ${it.date}",it.date)};alerts=Watchlist.mergeAlerts(alerts,hits)}
        }catch(e:Exception){if(e is CancellationException)throw e;if(symbol==selected)eventStatus="事件源暂不可用；未读取到不能视为没有异动"}
    }
    fun addEvent(date:String,kind:String,direction:Direction,url:String) {val d=LocalDate.parse(date);require(url.startsWith("https://")){"请填写原公告 HTTPS 链接"};events=events.filterNot {it.symbol==selected&&it.date==d&&it.kind==kind}+OfficialEvent("$selected-$d-$kind",selected,d,kind,direction,url,true);store.saveEvents(events);message="已按你核对的原公告保存"}
    fun recent(s:MarketSnapshot)=s.recent(events)
    fun import(text:String) {val count=store.import(text);watches=store.watchlist();theme=store.value("theme","system");large=store.value("large","false")=="true";threshold=store.value("threshold","1");message="备份已合并，共 $count 只自选"}
    fun undoImport(){if(store.undoImport()){watches=store.watchlist();theme=store.value("theme","system");large=store.value("large","false")=="true";threshold=store.value("threshold","1");message="已恢复导入前自选与设置"}else message="暂无可恢复备份"}
    fun historySnapshot(date:String):MarketSnapshot?=store.cached(selected,date)
    fun clearHistory(){history=emptyList();store.put("recent","")}
}
