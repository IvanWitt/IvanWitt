'use strict';

/* v1.2.9 — robust fixed modal for Calendar 13 Moons + live event-table sync. */
let moonModalSavedBodyOverflow='';
let moonModalSavedHtmlOverflow='';
let moonModalActiveOrdinal=0;

function applyMoonModalCriticalStyles(modal){
  if(!modal) return;
  modal.style.cssText='position:fixed!important;inset:0!important;z-index:10000!important;display:none;align-items:center!important;justify-content:center!important;padding:14px!important;box-sizing:border-box!important;overflow:hidden!important;';
  const backdrop=modal.querySelector('.moon-day-backdrop');
  if(backdrop) backdrop.style.cssText='position:absolute!important;inset:0!important;background:rgba(14,19,16,.74)!important;backdrop-filter:blur(2px)!important;-webkit-backdrop-filter:blur(2px)!important;';
  const card=modal.querySelector('.moon-day-card');
  if(card) card.style.cssText='position:relative!important;z-index:1!important;width:min(640px,calc(100vw - 24px))!important;max-height:calc(100vh - 110px)!important;overflow-y:auto!important;box-sizing:border-box!important;border:2px solid #a77b3f!important;border-radius:20px!important;padding:14px!important;background:linear-gradient(160deg,#fffaf0,#eadcc0)!important;box-shadow:0 18px 55px rgba(0,0,0,.38)!important;overscroll-behavior:contain!important;';
  const header=modal.querySelector('.moon-day-header');
  if(header) header.style.cssText='position:sticky!important;top:-14px!important;z-index:4!important;display:flex!important;align-items:center!important;gap:8px!important;margin:-14px -14px 12px!important;padding:10px 12px!important;background:rgba(250,241,220,.98)!important;border-bottom:1px solid #c9ab75!important;backdrop-filter:blur(6px)!important;';
  const title=modal.querySelector('.moon-day-title');
  if(title) title.style.cssText='flex:1!important;font-size:18px!important;font-weight:900!important;color:#3f2c16!important;white-space:nowrap!important;overflow:hidden!important;text-overflow:ellipsis!important;';
  const close=modal.querySelector('.moon-day-close');
  if(close) close.style.cssText='width:38px!important;height:38px!important;min-width:38px!important;border-radius:50%!important;border:1px solid #a77b3f!important;background:#f7edda!important;color:#5b3c1d!important;font-size:25px!important;line-height:1!important;';
  const save=modal.querySelector('.moon-day-save');
  if(save) save.style.cssText='min-height:38px!important;padding:8px 14px!important;border-radius:10px!important;border:1px solid #315b52!important;background:#315b52!important;color:#fff6df!important;font-size:14px!important;font-weight:900!important;';
  const dateGrid=modal.querySelector('.moon-day-dategrid');
  if(dateGrid) dateGrid.style.cssText='display:grid!important;grid-template-columns:repeat(2,minmax(0,1fr))!important;gap:8px!important;padding:0!important;';
  modal.querySelectorAll('.moon-day-dategrid>div').forEach(el=>el.style.cssText='min-width:0!important;padding:9px 10px!important;border:1px solid #ceb98e!important;border-radius:11px!important;background:rgba(255,255,255,.68)!important;');
  modal.querySelectorAll('.moon-day-dategrid span').forEach(el=>el.style.cssText='display:block!important;font-size:10px!important;font-weight:800!important;color:#806b4e!important;');
  modal.querySelectorAll('.moon-day-dategrid strong').forEach(el=>el.style.cssText='display:block!important;margin-top:2px!important;font-size:14px!important;line-height:1.25!important;color:#2f3b34!important;overflow-wrap:anywhere!important;');
  const editor=modal.querySelector('.moon-event-editor');
  if(editor) editor.style.cssText='display:grid!important;grid-template-columns:82px minmax(0,1fr) 165px!important;gap:7px!important;align-items:center!important;margin-top:13px!important;padding:9px!important;border:1px solid #c6ad7d!important;border-radius:12px!important;background:#fff!important;box-sizing:border-box!important;';
  const day=modal.querySelector('.moon-event-day');
  if(day) day.style.cssText='font-weight:900!important;text-align:center!important;color:#4f3a21!important;';
  const input=modal.querySelector('#moonEventName');
  if(input) input.style.cssText='width:100%!important;min-width:0!important;min-height:42px!important;box-sizing:border-box!important;background:rgba(255,255,255,.96)!important;';
  const select=modal.querySelector('#moonEventColor');
  if(select) select.style.cssText='width:100%!important;min-width:0!important;min-height:42px!important;box-sizing:border-box!important;background:rgba(255,255,255,.96)!important;';
  if(window.innerWidth<=600){
    if(dateGrid) dateGrid.style.gridTemplateColumns='1fr';
    if(editor) editor.style.gridTemplateColumns='1fr';
    if(day) day.style.textAlign='left';
    if(card) card.style.maxHeight='calc(100dvh - 32px)';
  }
}

ensureMoonDayModal=function(){
  let modal=document.getElementById('moonDayModal');
  if(modal) modal.remove();
  modal=document.createElement('div');
  modal.id='moonDayModal';
  modal.className='moon-day-modal moon-day-modal-fixed';
  modal.hidden=true;
  modal.setAttribute('aria-hidden','true');
  modal.innerHTML=`
    <div class="moon-day-backdrop" data-close-moon-day></div>
    <div class="moon-day-card" role="dialog" aria-modal="true" aria-labelledby="moonDayTitle">
      <div class="moon-day-header">
        <button class="moon-day-close" type="button" data-close-moon-day aria-label="Закрыть">×</button>
        <div class="moon-day-title" id="moonDayTitle">День</div>
        <button class="moon-day-save" id="moonDaySave" type="button">Сохранить</button>
      </div>
      <div class="moon-day-dategrid">
        <div><span>Григорианская</span><strong id="moonModalGregorian">—</strong></div>
        <div><span>Цолькин</span><strong id="moonModalTzolkin">—</strong></div>
        <div><span>Хааб</span><strong id="moonModalHaab">—</strong></div>
        <div><span>13 лун</span><strong id="moonModal13">—</strong></div>
      </div>
      <div class="moon-event-editor" id="moonEventEditor">
        <div class="moon-event-day" id="moonEventOrdinal">—</div>
        <input id="moonEventName" type="text" maxlength="160" placeholder="Праздник или событие" aria-label="Праздник или событие">
        <select id="moonEventColor" aria-label="Цвет события"></select>
      </div>
    </div>`;
  document.body.appendChild(modal);
  applyMoonModalCriticalStyles(modal);
  modal.querySelectorAll('[data-close-moon-day]').forEach(el=>el.addEventListener('click',closeMoonDayModal));
  modal.querySelector('#moonDaySave').addEventListener('click',()=>{
    saveMoonDayEventFromModal();
    closeMoonDayModal();
  });
  window.addEventListener('resize',()=>applyMoonModalCriticalStyles(modal));
};

function syncEventsPageImmediately(){
  if(typeof buildEventsTable==='function') buildEventsTable();
  const eventsPage=document.getElementById('events');
  if(eventsPage){
    eventsPage.dataset.eventsRevision=String(Date.now());
  }
}

function saveMoonDayEventFromModal(){
  const ordinal=moonModalActiveOrdinal;
  if(!ordinal) return;
  const input=document.getElementById('moonEventName');
  const select=document.getElementById('moonEventColor');
  const editor=document.getElementById('moonEventEditor');
  if(!input||!select) return;
  const clean=input.value;
  const color=select.value;
  const key=String(ordinal);
  if(clean||color) events[key]={text:clean,color}; else delete events[key];
  saveEvents();
  if(editor) editor.style.background=color||'#fff';
  updateCustomCalendar();
  syncEventsPageImmediately();
}

closeMoonDayModal=function(){
  const modal=document.getElementById('moonDayModal');
  if(modal){
    modal.hidden=true;
    modal.setAttribute('aria-hidden','true');
    modal.style.display='none';
  }
  document.body.style.overflow=moonModalSavedBodyOverflow;
  document.documentElement.style.overflow=moonModalSavedHtmlOverflow;
  moonModalActiveOrdinal=0;
  syncEventsPageImmediately();
};

openMoonDayModal=function(ordinal){
  if(!Number.isInteger(Number(ordinal)) || Number(ordinal)<1 || Number(ordinal)>365) return;
  let modal=document.getElementById('moonDayModal');
  if(!modal){ ensureMoonDayModal(); modal=document.getElementById('moonDayModal'); }
  moonModalActiveOrdinal=Number(ordinal);
  const year=moonDisplayYear();
  const date=moonGregorianForOrdinal(moonModalActiveOrdinal,year);
  const maya=mayaForGregorianDate(date);
  const moonState=moonStateForDate(date);
  document.getElementById('moonDayTitle').textContent=moonModalActiveOrdinal===365?'День вне времени':`${moonState.dayInMonth} Moon ${moonState.monthIndex+1}`;
  document.getElementById('moonModalGregorian').textContent=moonShortGregorian(date);
  document.getElementById('moonModalTzolkin').textContent=maya.tzolkin;
  document.getElementById('moonModalHaab').textContent=`${maya.haab} (${maya.haabOrdinal})`;
  document.getElementById('moonModal13').textContent=moonLabel(moonState,true);
  document.getElementById('moonEventOrdinal').textContent=`День ${moonModalActiveOrdinal}`;
  const input=document.getElementById('moonEventName');
  const select=document.getElementById('moonEventColor');
  select.innerHTML=eventColors.map(([hex,name])=>`<option value="${hex}">${name}</option>`).join('');
  const saved=eventForDay(moonModalActiveOrdinal);
  input.value=saved.text||'';
  select.value=saved.color||'';
  input.oninput=saveMoonDayEventFromModal;
  select.onchange=saveMoonDayEventFromModal;
  const editor=document.getElementById('moonEventEditor');
  if(editor) editor.style.background=saved.color||'#fff';
  moonModalSavedBodyOverflow=document.body.style.overflow;
  moonModalSavedHtmlOverflow=document.documentElement.style.overflow;
  document.body.style.overflow='hidden';
  document.documentElement.style.overflow='hidden';
  modal.hidden=false;
  modal.setAttribute('aria-hidden','false');
  applyMoonModalCriticalStyles(modal);
  modal.style.display='flex';
};

closeEventModal=function(){
  closeMoonDayModal();
  const legacy=document.getElementById('eventModal');
  if(legacy) legacy.hidden=true;
};

(function installMoonModalFixStyles(){
  const old=document.getElementById('moonCalendarModalFixStyles');
  if(old) old.remove();
  const style=document.createElement('style');
  style.id='moonCalendarModalFixStyles';
  style.textContent=`
    body.moon-modal-open{overflow:hidden!important}
    .moon-day-modal-fixed[hidden]{display:none!important}
    .moon-day-modal-fixed{position:fixed!important;inset:0!important;z-index:10000!important;align-items:center!important;justify-content:center!important}
    .moon-day-modal-fixed .moon-day-save{cursor:pointer}
    @media(max-width:600px){
      .moon-day-modal-fixed{padding:8px!important}
      .moon-day-modal-fixed .moon-day-card{width:calc(100vw - 16px)!important;max-height:calc(100dvh - 16px)!important}
      .moon-day-modal-fixed .moon-day-dategrid{grid-template-columns:1fr!important}
      .moon-day-modal-fixed .moon-event-editor{grid-template-columns:1fr!important}
    }
  `;
  document.head.appendChild(style);
})();
