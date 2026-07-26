'use strict';

/* Calendar 13 Moons v2: configurable Gregorian epoch, 13×28 + day 365. */
const MOON_START_STORAGE_KEY = 'maya-calendar-13moons-start-v2';
const MOON_YEAR_DAYS = 365;
const MOON_MONTH_DAYS = 28;
const MOON_MONTH_COUNT = 13;
const RU_MONTHS_SHORT = ['янв','фев','мар','апр','май','июн','июл','авг','сен','окт','ноя','дек'];

function moonLocalDate(y,m,d){ return new Date(y,m-1,d,12,0,0,0); }
function moonUtcDay(date){ return Math.floor(Date.UTC(date.getFullYear(),date.getMonth(),date.getDate())/DAY_MS); }
function moonDateFromInput(value){
  const parts=String(value||'').split('-').map(Number);
  if(parts.length!==3 || parts.some(Number.isNaN)) return null;
  const date=moonLocalDate(parts[0],parts[1],parts[2]);
  if(date.getFullYear()!==parts[0] || date.getMonth()!==parts[1]-1 || date.getDate()!==parts[2]) return null;
  return date;
}
function moonInputValue(date){ return `${date.getFullYear()}-${pad(date.getMonth()+1)}-${pad(date.getDate())}`; }
function moonAddDays(date,days){
  const utc=Date.UTC(date.getFullYear(),date.getMonth(),date.getDate())+days*DAY_MS;
  const d=new Date(utc);
  return moonLocalDate(d.getUTCFullYear(),d.getUTCMonth()+1,d.getUTCDate());
}
function moonStoredStartValue(){
  try { return localStorage.getItem(MOON_START_STORAGE_KEY)||''; } catch { return ''; }
}
function moonStartDate(){
  const input=document.getElementById('moonStartDate');
  const fromInput=moonDateFromInput(input && input.value);
  if(fromInput) return fromInput;
  const stored=moonDateFromInput(moonStoredStartValue());
  if(stored) return stored;
  return moonLocalDate(new Date().getFullYear(),new Date().getMonth()+1,new Date().getDate());
}
function moonSaveStart(date){
  const value=moonInputValue(date);
  try { localStorage.setItem(MOON_START_STORAGE_KEY,value); } catch {}
  return value;
}
function moonStateForDate(date){
  const start=moonStartDate();
  const elapsed=moonUtcDay(date)-moonUtcDay(start);
  if(elapsed<0){
    return {beforeStart:true,start,elapsed,year:0,ordinal:0,monthIndex:-1,dayInMonth:0,outside:false};
  }
  const year=Math.floor(elapsed/MOON_YEAR_DAYS)+1;
  const ordinal=(elapsed%MOON_YEAR_DAYS)+1;
  const outside=ordinal===MOON_YEAR_DAYS;
  return {
    beforeStart:false,start,elapsed,year,ordinal,outside,
    monthIndex:outside?-1:Math.floor((ordinal-1)/MOON_MONTH_DAYS),
    dayInMonth:outside?0:((ordinal-1)%MOON_MONTH_DAYS)+1
  };
}
function moonLabel(state,includeOrdinal=false){
  if(state.beforeStart) return 'До начала календаря';
  if(state.outside) return `День вне времени${includeOrdinal?' · день 365/365':''} (год ${state.year})`;
  const core=`${state.dayInMonth} Moon ${state.monthIndex+1}`;
  return `${core}${includeOrdinal?` · день ${state.ordinal}/365`:''} (год ${state.year})`;
}
function moonDisplayYear(){
  const s=moonStateForDate(new Date());
  return s.beforeStart?1:s.year;
}
function moonGregorianForOrdinal(ordinal,year=moonDisplayYear()){
  return moonAddDays(moonStartDate(),(year-1)*MOON_YEAR_DAYS+(ordinal-1));
}
function moonShortGregorian(date){
  const yy=String(mod(date.getFullYear(),100)).padStart(2,'0');
  return `${date.getDate()} ${RU_MONTHS_SHORT[date.getMonth()]} ${yy}`;
}
function mayaForGregorianDate(date){
  const correlation=Math.trunc(Number(document.getElementById('correlation').value)||584283);
  const y=date.getFullYear(),m=date.getMonth()+1,d=date.getDate();
  const jdn=gregorianToJdn(y,m,d);
  const days=jdn-correlation;
  const tone=mod(days+3,13)+1;
  const signIndex=mod(days+19,20);
  const haabIndex=mod(days+348,365);
  const haabMonthIndex=Math.floor(haabIndex/20);
  const haabDay=mod(haabIndex,20);
  return {
    tzolkin:`${tone} ${dayNames[signIndex]}`,
    haab:`${haabDay} ${haabMonths[haabMonthIndex]}`,
    haabOrdinal:haabIndex+1
  };
}

function ensureMoonUi(){
  const section=document.getElementById('custom');
  if(!section) return;
  const body=section.querySelector('.section-body');
  if(!body) return;
  const note=section.querySelector('.section-note');
  if(note) note.textContent='13 × 28 дней + День вне времени';

  let controls=document.getElementById('moonCalendarControls');
  if(!controls){
    controls=document.createElement('div');
    controls.id='moonCalendarControls';
    controls.className='moon-calendar-controls';
    controls.innerHTML=`
      <div class="field moon-start-field">
        <label for="moonStartDate">Стартовая григорианская дата</label>
        <input id="moonStartDate" type="date" aria-describedby="moonStartHint">
        <small id="moonStartHint">Эта дата = день 1 / Moon 1 / год 1.</small>
      </div>
      <div class="moon-today-card">
        <span>Сегодня по календарю 13 лун</span>
        <strong id="moonTodayDate">—</strong>
        <small id="moonTodayMeta">—</small>
      </div>`;
    body.insertBefore(controls,body.firstChild);
    const input=controls.querySelector('#moonStartDate');
    const initial=moonDateFromInput(moonStoredStartValue()) || moonLocalDate(new Date().getFullYear(),new Date().getMonth()+1,new Date().getDate());
    input.value=moonSaveStart(initial);
    input.addEventListener('change',()=>{
      const parsed=moonDateFromInput(input.value);
      if(!parsed){ input.value=moonInputValue(moonStartDate()); return; }
      moonSaveStart(parsed);
      updateMoonTodayHeader();
      updateCustomCalendar();
      if(typeof updateAll==='function') updateAll();
    });
  }

  const oldInfo=body.querySelector('p[style*="7f0000"]');
  if(oldInfo) oldInfo.textContent='Дни Moon 1–13 нумеруются от 1 до 28. События синхронизированы со страницей «События».';
  ensureMoonDayModal();
  updateMoonTodayHeader();
}
function updateMoonTodayHeader(){
  const main=document.getElementById('moonTodayDate');
  const meta=document.getElementById('moonTodayMeta');
  if(!main||!meta) return;
  const state=moonStateForDate(new Date());
  if(state.beforeStart){
    main.textContent='Календарь ещё не начался';
    meta.textContent=`Старт: ${moonShortGregorian(state.start)}`;
    return;
  }
  main.textContent=state.outside?'День вне времени':`${state.dayInMonth} Moon ${state.monthIndex+1}`;
  meta.textContent=`Год ${state.year} · день ${state.ordinal}/365`;
}

format13MoonDate=function(state){
  const s=moonStateForDate(moonLocalDate(state.y,state.m,state.d));
  if(s.beforeStart) return 'До старта';
  return s.outside?`День вне времени / год ${s.year}`:`${s.dayInMonth}/Moon ${s.monthIndex+1}/${s.year}`;
};

buildCustomCalendar=function(){
  ensureMoonUi();
  const grid=document.getElementById('customGrid');
  grid.innerHTML='';
  for(let m=0;m<MOON_MONTH_COUNT;m++){
    const article=document.createElement('article');
    article.className='custom-month';
    const h=document.createElement('h3');
    h.textContent=`Moon ${m+1}`;
    h.style.background=customPalette[m];
    const table=document.createElement('table');
    table.innerHTML=`<thead><tr><th></th>${weekdays.map((wd,i)=>`<th class="${i>=5?'weekend-head':''}">${wd}</th>`).join('')}</tr></thead>`;
    const tbody=document.createElement('tbody');
    for(let w=0;w<4;w++){
      const tr=document.createElement('tr');
      const label=document.createElement('td');
      label.className='week-label';
      label.textContent=`Нед. ${w+1}`;
      tr.appendChild(label);
      for(let d=0;d<7;d++){
        const dayInMonth=w*7+d+1;
        const ordinal=m*MOON_MONTH_DAYS+dayInMonth;
        const td=document.createElement('td');
        td.className='custom-day'+(d>=5?' weekend':'');
        td.dataset.day=String(ordinal);
        td.dataset.moon=String(m+1);
        td.dataset.dayInMoon=String(dayInMonth);
        td.textContent=String(dayInMonth);
        tr.appendChild(td);
      }
      tbody.appendChild(tr);
    }
    table.appendChild(tbody);
    article.append(h,table);
    grid.appendChild(article);
  }
  const outside=document.createElement('article');
  outside.className='outside-time';
  outside.dataset.day='365';
  outside.dataset.outside='true';
  outside.innerHTML='<div>День вне времени<small>365-й день года</small></div>';
  grid.appendChild(outside);
  updateCustomCalendar();
};

updateCustomCalendar=function(){
  const today=moonStateForDate(new Date());
  document.querySelectorAll('#customGrid [data-day]').forEach(cell=>{
    const ordinal=Number(cell.dataset.day);
    const event=events[ordinal]||{name:'',color:''};
    cell.style.background=event.color||'';
    cell.classList.toggle('has-event',Boolean(event.name));
    cell.classList.toggle('current-day',!today.beforeStart && ordinal===today.ordinal);
    if(ordinal===365){
      cell.title=event.name?`День вне времени: ${event.name}`:'День вне времени';
    } else {
      const dayInMoon=Number(cell.dataset.dayInMoon);
      const moon=Number(cell.dataset.moon);
      cell.title=event.name?`${dayInMoon} Moon ${moon}: ${event.name}`:`${dayInMoon} Moon ${moon}`;
    }
  });
  updateMoonTodayHeader();
};

function ensureMoonDayModal(){
  if(document.getElementById('moonDayModal')) return;
  const modal=document.createElement('div');
  modal.id='moonDayModal';
  modal.className='moon-day-modal';
  modal.hidden=true;
  modal.innerHTML=`
    <div class="moon-day-backdrop" data-close-moon-day></div>
    <div class="moon-day-card" role="dialog" aria-modal="true" aria-labelledby="moonDayTitle">
      <button class="moon-day-close" type="button" data-close-moon-day aria-label="Закрыть">×</button>
      <div class="moon-day-dategrid">
        <div><span>Григорианская</span><strong id="moonModalGregorian">—</strong></div>
        <div><span>Цолькин</span><strong id="moonModalTzolkin">—</strong></div>
        <div><span>Хааб</span><strong id="moonModalHaab">—</strong></div>
        <div><span>13 лун</span><strong id="moonModal13">—</strong></div>
      </div>
      <div class="moon-event-editor" id="moonEventEditor">
        <div class="moon-event-day" id="moonEventOrdinal">—</div>
        <input id="moonEventName" type="text" maxlength="160" aria-label="Праздник или событие">
        <select id="moonEventColor" aria-label="Цвет события"></select>
      </div>
    </div>`;
  document.body.appendChild(modal);
  modal.querySelectorAll('[data-close-moon-day]').forEach(el=>el.addEventListener('click',closeMoonDayModal));
}
function closeMoonDayModal(){ const modal=document.getElementById('moonDayModal'); if(modal) modal.hidden=true; }
closeEventModal=function(){
  closeMoonDayModal();
  const legacy=document.getElementById('eventModal'); if(legacy) legacy.hidden=true;
};
function openMoonDayModal(ordinal){
  ensureMoonDayModal();
  const year=moonDisplayYear();
  const date=moonGregorianForOrdinal(ordinal,year);
  const maya=mayaForGregorianDate(date);
  const moonState=moonStateForDate(date);
  document.getElementById('moonModalGregorian').textContent=moonShortGregorian(date);
  document.getElementById('moonModalTzolkin').textContent=maya.tzolkin;
  document.getElementById('moonModalHaab').textContent=`${maya.haab} (${maya.haabOrdinal})`;
  document.getElementById('moonModal13').textContent=moonLabel(moonState,true);
  document.getElementById('moonEventOrdinal').textContent=`День ${ordinal}`;
  const input=document.getElementById('moonEventName');
  const select=document.getElementById('moonEventColor');
  select.innerHTML=eventColors.map(([hex,name])=>`<option value="${hex}">${name}</option>`).join('');
  const saved=events[ordinal]||{};
  input.value=saved.name||'';
  select.value=saved.color||'';
  const editor=document.getElementById('moonEventEditor');
  function save(){
    const clean=input.value.trim();
    const color=select.value;
    if(clean||color) events[ordinal]={name:clean,color}; else delete events[ordinal];
    persistEvents();
    editor.style.background=color||'';
    refreshEventsTable();
  }
  input.oninput=save;
  select.onchange=save;
  editor.style.background=saved.color||'';
  document.getElementById('moonDayModal').hidden=false;
  setTimeout(()=>input.focus(),40);
}

initCustomEventPopups=function(){
  ensureMoonUi();
  document.getElementById('customGrid').addEventListener('click',e=>{
    const cell=e.target.closest('[data-day]');
    if(!cell || !document.getElementById('customGrid').contains(cell)) return;
    openMoonDayModal(Number(cell.dataset.day));
  });
  document.addEventListener('keydown',e=>{ if(e.key==='Escape') closeMoonDayModal(); });
};

(function installMoonCalendarStyles(){
  const style=document.createElement('style');
  style.id='moonCalendarV2Styles';
  style.textContent=`
    .moon-calendar-controls{display:grid;grid-template-columns:minmax(210px,.85fr) minmax(220px,1.15fr);gap:12px;align-items:stretch;margin-bottom:14px;padding:12px;border:1px solid #c7ae7b;border-radius:14px;background:linear-gradient(145deg,#fffaf0,#e9ddc4);box-shadow:0 4px 12px rgba(73,51,24,.08)}
    .moon-start-field input{width:100%;background:#fffdf7;border-color:#b99a62;font-weight:800}.moon-start-field small{font-size:11px;color:#77664b;line-height:1.2}
    .moon-today-card{display:flex;flex-direction:column;justify-content:center;padding:10px 13px;border-radius:12px;background:linear-gradient(135deg,#315b52,#6e542e);color:#f7e8c2;border:1px solid #9f7a40}.moon-today-card span{font-size:11px;opacity:.82}.moon-today-card strong{font-size:19px;line-height:1.15;margin:3px 0}.moon-today-card small{font-size:12px;color:#f5ddb0}
    #customGrid .custom-month h3{font-weight:900;color:#3b2b18;border-bottom:1px solid rgba(83,54,24,.18)}#customGrid .custom-day{cursor:pointer;font-size:15px}#customGrid .outside-time{cursor:pointer}#customGrid .outside-time small{display:block;margin-top:6px;font-size:12px;font-weight:700;opacity:.72}
    .moon-day-modal[hidden]{display:none}.moon-day-modal{position:fixed;inset:0;z-index:760;display:flex;align-items:center;justify-content:center;padding:14px}.moon-day-backdrop{position:absolute;inset:0;background:rgba(14,19,16,.70);backdrop-filter:blur(2px)}
    .moon-day-card{position:relative;z-index:1;width:min(620px,96vw);border:2px solid #a77b3f;border-radius:20px;padding:18px;background:linear-gradient(160deg,#fffaf0,#eadcc0);box-shadow:0 18px 55px rgba(0,0,0,.36)}.moon-day-close{position:absolute;right:10px;top:9px;width:42px;height:42px;border-radius:50%;border:1px solid #a77b3f;background:#f7edda;color:#5b3c1d;font-size:27px;line-height:1;z-index:2}
    .moon-day-dategrid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;padding-right:42px}.moon-day-dategrid>div{min-width:0;padding:9px 10px;border:1px solid #ceb98e;border-radius:11px;background:rgba(255,255,255,.62)}.moon-day-dategrid span{display:block;font-size:10px;font-weight:800;color:#806b4e}.moon-day-dategrid strong{display:block;margin-top:2px;font-size:14px;line-height:1.2;color:#2f3b34;overflow-wrap:anywhere}
    .moon-event-editor{display:grid;grid-template-columns:82px minmax(0,1fr) 165px;gap:7px;align-items:center;margin-top:13px;padding:9px;border:1px solid #c6ad7d;border-radius:12px;background:#fff}.moon-event-day{font-weight:900;text-align:center;color:#4f3a21}.moon-event-editor input,.moon-event-editor select{width:100%;min-height:40px;background:rgba(255,255,255,.92)}
    @media(max-width:600px){.moon-calendar-controls{grid-template-columns:1fr}.moon-day-dategrid{grid-template-columns:1fr;padding-right:40px}.moon-day-card{padding:15px 12px}.moon-event-editor{grid-template-columns:1fr}.moon-event-day{text-align:left}.moon-day-dategrid strong{font-size:13px}}
  `;
  document.head.appendChild(style);
})();
