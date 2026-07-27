'use strict';

/* v1.3.2 — compact full-width events table with color-highlighted rows. */
(function installEventsTableV2(){
  function addStyles(){
    if(document.getElementById('eventsTableV2Styles')) return;
    const style=document.createElement('style');
    style.id='eventsTableV2Styles';
    style.textContent=`
      #events .events-scroll{width:100%;max-width:100%;overflow-y:auto;overflow-x:hidden}
      #events .events-table{width:100%;min-width:0!important;table-layout:fixed}
      #events .events-table th,#events .events-table td{padding:6px;transition:background-color .15s ease}
      #events .events-table th:first-child,#events .events-table td:first-child{width:15%;min-width:50px;text-align:center;font-weight:800}
      #events .events-table th:nth-child(2),#events .events-table td:nth-child(2){width:63%;min-width:0}
      #events .events-table th:nth-child(3),#events .events-table td:nth-child(3){width:22%;min-width:66px}
      #events .events-table input{width:100%;min-width:0;min-height:38px;background:rgba(255,255,255,.82);color:var(--ink);border:1px solid rgba(117,127,122,.42)}
      #events .event-color-cell{position:relative;padding:0!important;overflow:hidden}
      #events .event-color-cell::after{content:'';position:absolute;inset:5px;border:1px solid rgba(57,54,43,.28);border-radius:8px;pointer-events:none;box-shadow:inset 0 1px 2px rgba(255,255,255,.32)}
      #events .event-color-select{position:absolute;inset:0;width:100%;height:100%;min-height:0!important;opacity:0;cursor:pointer}
      #events .event-color-select option{color:#222;background:#fff}
      #events .events-table tr.event-colored-row td{background-color:var(--event-row-color)!important}
      #events .events-table tr.event-colored-row td:nth-child(2){background-color:var(--event-row-color)!important}
      #events .events-table tr.event-colored-row td:nth-child(2) input{background:rgba(255,255,255,.78)}
      @media(max-width:520px){
        #events .section-body{padding-left:8px;padding-right:8px}
        #events .events-table th,#events .events-table td{padding:4px}
        #events .events-table th:first-child,#events .events-table td:first-child{width:14%;min-width:44px}
        #events .events-table th:nth-child(2),#events .events-table td:nth-child(2){width:64%}
        #events .events-table th:nth-child(3),#events .events-table td:nth-child(3){width:22%;min-width:58px}
        #events .events-table input{min-height:36px;padding:6px 7px}
      }
    `;
    document.head.appendChild(style);
  }

  function normalizeHeaders(){
    const headers=document.querySelectorAll('#eventsTable thead th');
    if(headers[0]) headers[0].textContent='День';
    if(headers[1]) headers[1].textContent='Событие';
    if(headers[2]) headers[2].textContent='Цвет';
  }

  function paintRow(tr,color){
    const value=String(color||'').trim();
    if(value){
      tr.classList.add('event-colored-row');
      tr.style.setProperty('--event-row-color',value);
    }else{
      tr.classList.remove('event-colored-row');
      tr.style.removeProperty('--event-row-color');
    }
    const colorCell=tr.querySelector('.event-color-cell');
    if(colorCell) colorCell.style.backgroundColor=value||'';
  }

  function buildEventsTableV2(){
    const tbody=document.querySelector('#eventsTable tbody');
    if(!tbody) return;
    normalizeHeaders();
    tbody.innerHTML='';
    for(let day=1;day<=365;day++){
      const tr=document.createElement('tr');
      const tdDay=document.createElement('td');
      const tdEvent=document.createElement('td');
      const tdColor=document.createElement('td');
      tdColor.className='event-color-cell';
      tdDay.textContent=day;

      const input=document.createElement('input');
      input.type='text';
      input.value=eventForDay(day).text||'';
      input.setAttribute('aria-label',`Событие, день ${day}`);
      input.addEventListener('input',()=>{
        events[String(day)]={...eventForDay(day),text:input.value};
        saveEvents();
      });

      const select=document.createElement('select');
      select.className='event-color-select';
      select.setAttribute('aria-label',`Цвет события, день ${day}`);
      eventColors.forEach(([value,label])=>{
        const option=document.createElement('option');
        option.value=value;
        option.textContent=label;
        select.appendChild(option);
      });
      select.value=eventForDay(day).color||'';
      select.addEventListener('change',()=>{
        events[String(day)]={...eventForDay(day),color:select.value};
        paintRow(tr,select.value);
        saveEvents();
      });

      tdEvent.appendChild(input);
      tdColor.appendChild(select);
      tr.append(tdDay,tdEvent,tdColor);
      paintRow(tr,select.value);
      tbody.appendChild(tr);
    }
  }

  addStyles();
  buildEventsTable=buildEventsTableV2;
  window.buildEventsTableV2=buildEventsTableV2;
  if(document.readyState==='loading'){
    document.addEventListener('DOMContentLoaded',()=>{normalizeHeaders();buildEventsTableV2();},{once:true});
  }else{
    normalizeHeaders();
    buildEventsTableV2();
  }
})();
