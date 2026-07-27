'use strict';

/* v1.3.0 — isolated stone-and-wood calendar mechanism. */
(function installCalendarMechanismV2(){
  const state={offset:0,timer:null,drag:null,ready:false};
  const SVG='http://www.w3.org/2000/svg';
  const CYCLE=18980;
  const centers={tz:{x:280,y:555,cycle:260,dir:-1},haab:{x:610,y:455,cycle:365,dir:1},moon:{x:925,y:625,cycle:365,dir:-1}};

  function el(id){ return document.getElementById(id); }
  function pad2(n){ return String(n).padStart(2,'0'); }
  function dateValue(d){ return `${d.getFullYear()}-${pad2(d.getMonth()+1)}-${pad2(d.getDate())}`; }
  function parseLocalDate(value){
    const p=String(value||'').split('-').map(Number);
    if(p.length!==3 || p.some(Number.isNaN)) return new Date();
    return new Date(p[0],p[1]-1,p[2],12,0,0,0);
  }
  function addDaysLocal(date,days){
    const u=Date.UTC(date.getFullYear(),date.getMonth(),date.getDate())+days*86400000;
    const d=new Date(u);
    return new Date(d.getUTCFullYear(),d.getUTCMonth(),d.getUTCDate(),12,0,0,0);
  }
  function fmtDate(d){ return `${pad2(d.getDate())}.${pad2(d.getMonth()+1)}.${d.getFullYear()}`; }
  function correlationValue(){
    const n=Math.trunc(Number(el('gearCorrelation')?.value));
    return Number.isFinite(n)?n:584283;
  }
  function baseDate(){ return parseLocalDate(el('gearDate')?.value); }
  function effectiveDate(){ return addDaysLocal(baseDate(),state.offset); }
  function gearLocalState(){
    const d=effectiveDate();
    const corr=correlationValue();
    const jdn=gregorianToJdn(d.getFullYear(),d.getMonth()+1,d.getDate());
    const days=jdn-corr;
    const tone=mod(days+3,13)+1;
    const signIndex=mod(days+19,20);
    const kin=mod(days+159,260)+1;
    const hi=mod(days+348,365);
    const haabMonth=Math.floor(hi/20);
    const haabDay=mod(hi,20);
    const ordinal=hi+1;
    const outside=ordinal===365;
    const moon=outside?0:Math.floor((ordinal-1)/28)+1;
    const moonDay=outside?0:mod(ordinal-1,28)+1;
    return {date:d,corr,jdn,days,tone,signIndex,kin,hi,haabMonth,haabDay,ordinal,outside,moon,moonDay,long:longCount(days)};
  }

  function installStyles(){
    if(el('calendarMechanismV2Styles')) return;
    const style=document.createElement('style');
    style.id='calendarMechanismV2Styles';
    style.textContent=`
      #gears .section-head{background:linear-gradient(135deg,#284b43,#6f5632);border-bottom:1px solid #9f7d48}
      #gears .section-body{padding:12px;background:linear-gradient(180deg,#e8dfcb,#cbb992 54%,#9e865f)}
      .mechanism-shell{display:grid;gap:10px}
      .mechanism-top{display:grid;grid-template-columns:minmax(150px,1fr) minmax(150px,1fr);gap:9px}
      .mechanism-input-card{position:relative;padding:9px 11px;border:1px solid #7d5b32;border-radius:12px;background:linear-gradient(145deg,#8a5c2e,#5f3c20);box-shadow:inset 0 0 0 2px rgba(242,211,145,.16),0 5px 12px rgba(48,29,12,.22);color:#f5e0ad}
      .mechanism-input-card label{display:block;margin-bottom:4px;font:800 11px/1.1 Georgia,serif;letter-spacing:.06em;text-transform:uppercase;color:#e9cd91}
      .mechanism-input-card input{width:100%;min-height:38px;border:1px solid #bc965c;border-radius:8px;background:#f3e4c1;color:#3b2a18;font-weight:900;box-shadow:inset 0 2px 5px rgba(84,54,20,.16)}
      .mechanism-gmt-badge{position:absolute;right:17px;top:8px;padding:2px 6px;border-radius:999px;background:#274b43;color:#f5e4bd;font-size:9px;font-weight:900;letter-spacing:.08em}
      .mechanism-plaques{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px}
      .mechanism-plaque{min-width:0;padding:9px 9px 10px;border:1px solid #65401e;border-radius:10px;background:linear-gradient(160deg,#986632,#6b4526 55%,#50331e);box-shadow:inset 0 0 0 2px rgba(226,190,121,.16),0 4px 10px rgba(45,28,13,.22);color:#f7e3b4;text-align:center}
      .mechanism-plaque span{display:block;font:900 10px/1 Georgia,serif;letter-spacing:.08em;color:#dfbf80;text-transform:uppercase}
      .mechanism-plaque strong{display:block;margin-top:5px;font:900 clamp(12px,2vw,18px)/1.1 Georgia,serif;color:#fff0c7;overflow-wrap:anywhere;text-shadow:0 1px 1px #3e260f}
      .mechanism-plaque small{display:block;margin-top:3px;font-size:10px;color:#e5c991;font-weight:800}
      .mechanism-actions{display:flex;flex-wrap:wrap;align-items:end;gap:7px;padding:9px;border:1px solid #81643a;border-radius:12px;background:rgba(240,226,194,.82);box-shadow:0 4px 10px rgba(55,39,19,.12)}
      .mechanism-actions .btn{min-height:36px;padding:7px 10px;background:#375b51;border:1px solid #25443d;color:#fff2d2}
      .mechanism-actions .btn.secondary{background:#e4d1aa;color:#4a321b;border-color:#a88652}
      .mechanism-actions .field{min-width:110px}.mechanism-actions .field label{color:#675438;font-size:10px}
      .mechanism-actions select{min-height:36px;background:#fff7e7;border-color:#aa8a59;font-weight:800}
      .mechanism-slider-box{flex:1 1 260px;min-width:220px}.mechanism-slider-box input{width:100%;padding:0;border:0;box-shadow:none;accent-color:#355e52}
      .mechanism-position{min-width:150px;padding:8px 10px;border-radius:9px;background:#4c3621;color:#f6dfaa;text-align:center;font-weight:900;border:1px solid #7c5a31}
      .mechanism-stage{position:relative;min-height:620px;border:2px solid #654522;border-radius:18px;overflow:hidden;background:linear-gradient(#3c4a37,#28372c);box-shadow:inset 0 0 35px rgba(0,0,0,.42),0 9px 22px rgba(53,35,17,.2);touch-action:none;user-select:none}
      .mechanism-svg{display:block;width:100%;height:auto;min-height:620px;background:radial-gradient(circle at 50% 48%,rgba(173,164,119,.18),rgba(36,49,37,.05) 48%,rgba(12,22,17,.32))}
      .mechanism-wheel{cursor:grab;transform-box:view-box;transform-origin:center}.mechanism-wheel:active{cursor:grabbing}
      .stone-gear-body{fill:url(#stoneGear);stroke:#493d2d;stroke-width:5;filter:url(#stoneShadow)}
      .stone-ring{fill:none;stroke:#8d7c5a;stroke-width:18;opacity:.82}.stone-ring-inner{fill:none;stroke:#44392b;stroke-width:5;opacity:.9}
      .stone-hub{fill:url(#stoneHub);stroke:#3e3427;stroke-width:5}.stone-bolt{fill:#bda576;stroke:#59472d;stroke-width:2}
      .gear-label-main{fill:#f1dfb0;font:900 27px Georgia,serif;text-anchor:middle;paint-order:stroke;stroke:#342919;stroke-width:3}.gear-label-sub{fill:#d8bd82;font:800 14px Georgia,serif;text-anchor:middle;letter-spacing:.08em}
      .wood-pulley{fill:url(#woodPulley);stroke:#3f2816;stroke-width:5;filter:url(#woodShadow)}.wood-spoke{stroke:#6c421f;stroke-width:8;stroke-linecap:round}.wood-rim{fill:none;stroke:#d0a260;stroke-width:6;opacity:.72}
      .rope{fill:none;stroke:#b99b66;stroke-width:8;stroke-linecap:round;filter:url(#ropeShadow)}.rope-thin{fill:none;stroke:#745d3d;stroke-width:2;opacity:.65}
      .rack-body{fill:url(#rackStone);stroke:#493d2d;stroke-width:4;filter:url(#stoneShadow)}.rack-tooth{fill:#72674f;stroke:#463d2e;stroke-width:2}.rack-mark{stroke:#b7a47b;stroke-width:3;opacity:.72}
      .vine{fill:none;stroke:#42603d;stroke-width:7;stroke-linecap:round}.leaf{fill:#5f7f4d;stroke:#2f4c2e;stroke-width:2}
      .mechanism-caption{position:absolute;left:10px;bottom:9px;padding:7px 10px;border:1px solid rgba(215,187,128,.44);border-radius:9px;background:rgba(30,38,29,.76);color:#f0dbac;font-size:11px;font-weight:800;pointer-events:none}
      .mechanism-help{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:7px}.mechanism-help div{padding:8px 9px;border:1px solid #8f7449;border-radius:9px;background:#efe2c4;color:#4f3b25;font-size:11px;line-height:1.3}
      @media(max-width:760px){
        #gears .section-body{padding:8px}.mechanism-plaques{grid-template-columns:repeat(2,minmax(0,1fr))}.mechanism-plaque{padding:7px}.mechanism-top{grid-template-columns:1fr 1fr}.mechanism-stage{min-height:520px}.mechanism-svg{min-height:520px}.mechanism-help{grid-template-columns:1fr 1fr}.mechanism-actions{gap:5px}.mechanism-actions .btn{font-size:12px;padding:6px 8px}.mechanism-position{min-width:120px;font-size:11px}
      }
      @media(max-width:430px){.mechanism-top{grid-template-columns:1fr}.mechanism-plaques{grid-template-columns:1fr 1fr}.mechanism-help{grid-template-columns:1fr}.mechanism-slider-box{order:10;flex-basis:100%}}
    `;
    document.head.appendChild(style);
  }

  function mechanismMarkup(){
    return `
      <div class="section-head"><h2>Календарный механизм</h2><p class="section-note">Изолированная модель: Цолькин 52 зуба · Хааб 73 · 13 лун 73 · длинный счёт</p></div>
      <div class="section-body"><div class="mechanism-shell">
        <div class="mechanism-top">
          <div class="mechanism-input-card"><label for="gearDate">Григорианская дата</label><input id="gearDate" type="date"></div>
          <div class="mechanism-input-card"><span class="mechanism-gmt-badge">GMT</span><label for="gearCorrelation">Корреляция</label><input id="gearCorrelation" type="number" step="1" value="584283"></div>
        </div>
        <div class="mechanism-plaques">
          <div class="mechanism-plaque"><span>Цолькин</span><strong id="mechanismTzolkin">—</strong><small id="mechanismKin">—</small></div>
          <div class="mechanism-plaque"><span>Хааб</span><strong id="mechanismHaab">—</strong><small id="mechanismHaabOrdinal">—</small></div>
          <div class="mechanism-plaque"><span>Календарь 13 лун</span><strong id="mechanismMoon">—</strong><small id="mechanismMoonOrdinal">—</small></div>
          <div class="mechanism-plaque"><span>Длинный счёт</span><strong id="mechanismLongCount">—</strong><small id="mechanismLongMeta">—</small></div>
        </div>
        <div class="mechanism-actions">
          <button class="btn secondary" id="gearMinus20" type="button">−20</button>
          <button class="btn secondary" id="gearMinus1" type="button">−1</button>
          <button class="btn" id="gearPlay" type="button">▶ Авто</button>
          <button class="btn secondary" id="gearPlus1" type="button">+1</button>
          <button class="btn secondary" id="gearPlus20" type="button">+20</button>
          <button class="btn secondary" id="gearToday" type="button">Сегодня</button>
          <div class="field"><label for="gearStep">Шаг</label><select id="gearStep"><option value="1">1 день</option><option value="5">5 дней / 1 зуб</option><option value="13">13 дней</option><option value="20">20 дней</option><option value="28">28 дней</option><option value="52">52 дня</option><option value="73">73 дня</option><option value="260">260 дней</option><option value="365">365 дней</option><option value="18980">18 980 дней</option></select></div>
          <button class="btn secondary" id="gearMinusStep" type="button">− шаг</button>
          <button class="btn secondary" id="gearPlusStep" type="button">+ шаг</button>
          <div class="field mechanism-slider-box"><label for="gearSlider">Положение в локальном цикле 18 980 дней</label><input id="gearSlider" type="range" min="0" max="18979" value="0"></div>
          <div class="mechanism-position" id="gearPosition">Смещение 0 дней</div>
        </div>
        <div class="mechanism-stage" id="gearStage">
          <svg class="mechanism-svg" id="gearSvg" viewBox="0 0 1200 900" aria-label="Каменный календарный механизм майя">
            <defs>
              <linearGradient id="stoneGear" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#b0a27c"/><stop offset=".35" stop-color="#776c51"/><stop offset=".7" stop-color="#514a3a"/><stop offset="1" stop-color="#958665"/></linearGradient>
              <radialGradient id="stoneHub"><stop offset="0" stop-color="#a59670"/><stop offset=".7" stop-color="#655b46"/><stop offset="1" stop-color="#40392e"/></radialGradient>
              <linearGradient id="rackStone" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#94866a"/><stop offset=".55" stop-color="#625b49"/><stop offset="1" stop-color="#474237"/></linearGradient>
              <radialGradient id="woodPulley"><stop offset="0" stop-color="#c18a4d"/><stop offset=".55" stop-color="#8a572d"/><stop offset="1" stop-color="#4b2e19"/></radialGradient>
              <filter id="stoneShadow" x="-20%" y="-20%" width="140%" height="140%"><feDropShadow dx="0" dy="7" stdDeviation="6" flood-color="#0b0e0b" flood-opacity=".55"/></filter>
              <filter id="woodShadow" x="-30%" y="-30%" width="160%" height="160%"><feDropShadow dx="0" dy="5" stdDeviation="4" flood-color="#111" flood-opacity=".55"/></filter>
              <filter id="ropeShadow"><feDropShadow dx="0" dy="3" stdDeviation="2" flood-color="#111" flood-opacity=".5"/></filter>
            </defs>
            <rect x="0" y="0" width="1200" height="900" fill="#26382d"/>
            <path d="M0 160 Q240 110 470 160 T930 145 T1200 155" fill="none" stroke="#344934" stroke-width="52" opacity=".45"/>
            <g id="longRackBase"><g id="longRack"></g></g>
            <path class="rope" d="M600 236 C610 185 700 168 805 167 C925 165 1040 154 1115 120"/><path class="rope-thin" d="M600 236 C610 185 700 168 805 167 C925 165 1040 154 1115 120"/>
            <g id="pulleyA" transform="translate(610 252)"></g><g id="pulleyB" transform="translate(1070 125)"></g>
            <g id="tzGear" class="mechanism-wheel" data-mechanism-wheel="tz"></g>
            <g id="haabGear" class="mechanism-wheel" data-mechanism-wheel="haab"></g>
            <g id="moonGear" class="mechanism-wheel" data-mechanism-wheel="moon"></g>
            <path class="vine" d="M20 840 C95 790 102 720 145 660 C185 603 184 532 150 472"/>
            <ellipse class="leaf" cx="68" cy="785" rx="24" ry="10" transform="rotate(-28 68 785)"/><ellipse class="leaf" cx="112" cy="724" rx="24" ry="10" transform="rotate(31 112 724)"/><ellipse class="leaf" cx="137" cy="650" rx="23" ry="10" transform="rotate(-18 137 650)"/>
            <path class="vine" d="M1180 840 C1100 790 1112 715 1075 655"/><ellipse class="leaf" cx="1138" cy="785" rx="23" ry="10" transform="rotate(22 1138 785)"/><ellipse class="leaf" cx="1092" cy="718" rx="22" ry="10" transform="rotate(-32 1092 718)"/>
          </svg>
          <div class="mechanism-caption" id="mechanismEffectiveDate">Положение: —</div>
        </div>
        <div class="mechanism-help">
          <div><strong>Цолькин:</strong> 52 каменных зуба. Один зуб соответствует 5 дням 260-дневного цикла.</div>
          <div><strong>Хааб:</strong> центральное колесо на 73 зуба. Оно приводит длинную зубчатую рейку.</div>
          <div><strong>13 лун:</strong> 73 зуба, синхронно с 365-дневным ходом. День 365 — вне времени.</div>
          <div><strong>Изоляция:</strong> дата, корреляция и вращение изменяют только данные этой страницы.</div>
        </div>
      </div></div>`;
  }

  function svgNode(name,attrs={}){
    const n=document.createElementNS(SVG,name);
    Object.entries(attrs).forEach(([k,v])=>n.setAttribute(k,String(v)));
    return n;
  }
  function gearPath(teeth,rootR,outerR){
    const pts=[];
    for(let i=0;i<teeth;i++){
      const a=(i/teeth)*Math.PI*2-Math.PI/2;
      const next=((i+1)/teeth)*Math.PI*2-Math.PI/2;
      const q=(next-a)/4;
      [[a,rootR],[a+q,outerR],[a+3*q,outerR],[next,rootR]].forEach(([ang,r])=>pts.push([Math.cos(ang)*r,Math.sin(ang)*r]));
    }
    return pts.map((p,i)=>(i?'L':'M')+p[0].toFixed(2)+' '+p[1].toFixed(2)).join(' ')+' Z';
  }
  function addBolts(g,r,count){
    for(let i=0;i<count;i++){
      const a=i*2*Math.PI/count;
      g.appendChild(svgNode('circle',{class:'stone-bolt',cx:(Math.cos(a)*r).toFixed(1),cy:(Math.sin(a)*r).toFixed(1),r:5}));
    }
  }
  function buildStoneGear(group,teeth,cx,cy,rootR,outerR,title,sub){
    group.innerHTML='';
    group.dataset.cx=cx; group.dataset.cy=cy;
    group.appendChild(svgNode('path',{class:'stone-gear-body',d:gearPath(teeth,rootR,outerR)}));
    group.appendChild(svgNode('circle',{class:'stone-ring',r:rootR*.68}));
    group.appendChild(svgNode('circle',{class:'stone-ring-inner',r:rootR*.48}));
    group.appendChild(svgNode('circle',{class:'stone-hub',r:rootR*.31}));
    addBolts(group,rootR*.52,teeth===52?8:10);
    const t1=svgNode('text',{class:'gear-label-main',x:0,y:-5}); t1.textContent=title; group.appendChild(t1);
    const t2=svgNode('text',{class:'gear-label-sub',x:0,y:24}); t2.textContent=sub; group.appendChild(t2);
    group.setAttribute('transform',`translate(${cx} ${cy})`);
  }
  function buildPulley(group,r){
    group.innerHTML='';
    group.appendChild(svgNode('circle',{class:'wood-pulley',r}));
    group.appendChild(svgNode('circle',{class:'wood-rim',r:r-7}));
    for(let i=0;i<8;i++){
      const a=i*Math.PI/4;
      group.appendChild(svgNode('line',{class:'wood-spoke',x1:Math.cos(a)*12,y1:Math.sin(a)*12,x2:Math.cos(a)*(r-14),y2:Math.sin(a)*(r-14)}));
    }
    group.appendChild(svgNode('circle',{cx:0,cy:0,r:12,fill:'#392516',stroke:'#d3a363','stroke-width':4}));
  }
  function buildRack(group){
    group.innerHTML='';
    const rack=svgNode('g',{transform:'translate(-170 95)'});
    rack.appendChild(svgNode('rect',{class:'rack-body',x:0,y:25,width:1550,height:88,rx:16}));
    for(let i=0;i<70;i++){
      const x=i*23;
      rack.appendChild(svgNode('path',{class:'rack-tooth',d:`M ${x} 113 L ${x+7} 132 L ${x+16} 132 L ${x+23} 113 Z`}));
      if(i%5===0) rack.appendChild(svgNode('line',{class:'rack-mark',x1:x+4,y1:45,x2:x+18,y2:92}));
    }
    const label=svgNode('text',{x:610,y:79,'text-anchor':'middle',fill:'#d8c79a','font-family':'Georgia,serif','font-weight':'900','font-size':24});
    label.textContent='ДЛИННЫЙ СЧЁТ'; rack.appendChild(label);
    group.appendChild(rack);
  }
  function buildMechanismSvg(){
    buildRack(el('longRack'));
    buildPulley(el('pulleyA'),44); buildPulley(el('pulleyB'),34);
    buildStoneGear(el('tzGear'),52,centers.tz.x,centers.tz.y,132,154,'ЦОЛЬКИН','52 зуба');
    buildStoneGear(el('haabGear'),73,centers.haab.x,centers.haab.y,184,208,'ХААБ','73 зуба');
    buildStoneGear(el('moonGear'),73,centers.moon.x,centers.moon.y,176,199,'13 ЛУН','73 зуба');
  }

  function setWheelTransform(id,c,angle){ el(id)?.setAttribute('transform',`translate(${c.x} ${c.y}) rotate(${angle})`); }
  function renderMechanism(){
    if(!state.ready) return;
    const s=gearLocalState();
    el('mechanismTzolkin').textContent=`${s.tone} ${dayNames[s.signIndex]}`;
    el('mechanismKin').textContent=`КИН ${s.kin}`;
    el('mechanismHaab').textContent=`${s.haabDay} ${haabMonths[s.haabMonth]}`;
    el('mechanismHaabOrdinal').textContent=`день ${s.ordinal}/365`;
    el('mechanismMoon').textContent=s.outside?'День вне времени':`${s.moonDay} Moon ${s.moon}`;
    el('mechanismMoonOrdinal').textContent=`день ${s.ordinal}/365`;
    el('mechanismLongCount').textContent=s.long;
    el('mechanismLongMeta').textContent=`JDN ${s.jdn}`;
    el('mechanismEffectiveDate').textContent=`Положение: ${fmtDate(s.date)} · смещение ${state.offset>=0?'+':''}${state.offset} дн.`;
    el('gearPosition').textContent=`Смещение ${state.offset>=0?'+':''}${state.offset} дн.`;
    el('gearSlider').value=mod(state.offset,CYCLE);
    setWheelTransform('tzGear',centers.tz,-state.offset*360/260);
    setWheelTransform('haabGear',centers.haab,state.offset*360/365);
    setWheelTransform('moonGear',centers.moon,-state.offset*360/365);
    const rackShift=-mod(state.offset,20)*7;
    el('longRack').setAttribute('transform',`translate(${rackShift} 0)`);
    el('pulleyA').setAttribute('transform',`translate(610 252) rotate(${state.offset*18})`);
    el('pulleyB').setAttribute('transform',`translate(1070 125) rotate(${-state.offset*18})`);
  }
  function addOffset(n){ state.offset+=Number(n)||0; renderMechanism(); }
  function selectedStep(){ return Math.trunc(Number(el('gearStep')?.value)||1); }
  function stopAuto(){ if(state.timer){clearInterval(state.timer);state.timer=null;} if(el('gearPlay')) el('gearPlay').textContent='▶ Авто'; }
  function toggleAuto(){
    if(state.timer){ stopAuto(); return; }
    el('gearPlay').textContent='■ Стоп';
    state.timer=setInterval(()=>addOffset(selectedStep()),180);
  }
  function pointerToSvg(e){
    const svg=el('gearSvg'); const p=svg.createSVGPoint(); p.x=e.clientX;p.y=e.clientY;
    return p.matrixTransform(svg.getScreenCTM().inverse());
  }
  function attachDrag(group,key){
    const c=centers[key];
    group.addEventListener('pointerdown',e=>{
      const p=pointerToSvg(e);
      state.drag={group,key,last:Math.atan2(p.y-c.y,p.x-c.x),remainder:0};
      try{group.setPointerCapture(e.pointerId);}catch(_){}
      e.preventDefault();
    });
    group.addEventListener('pointermove',e=>{
      if(!state.drag || state.drag.group!==group) return;
      const p=pointerToSvg(e);
      const angle=Math.atan2(p.y-c.y,p.x-c.x);
      let diff=angle-state.drag.last;if(diff>Math.PI)diff-=Math.PI*2;if(diff<-Math.PI)diff+=Math.PI*2;
      state.drag.last=angle;
      state.drag.remainder += diff/(Math.PI*2)*c.cycle*c.dir;
      const whole=state.drag.remainder>0?Math.floor(state.drag.remainder):Math.ceil(state.drag.remainder);
      if(whole){state.drag.remainder-=whole;addOffset(whole);}
      e.preventDefault();
    });
    const end=e=>{if(state.drag&&state.drag.group===group){try{group.releasePointerCapture(e.pointerId);}catch(_){}state.drag=null;}};
    group.addEventListener('pointerup',end);group.addEventListener('pointercancel',end);
  }

  function installMarkup(){
    const section=el('gears'); if(!section) return false;
    section.innerHTML=mechanismMarkup();
    return true;
  }
  function initMechanism(){
    installStyles();
    if(!installMarkup()) return;
    el('gearDate').value=dateValue(new Date());
    el('gearCorrelation').value='584283';
    state.offset=0; state.ready=true;
    buildMechanismSvg();
    el('gearMinus20').onclick=()=>addOffset(-20);
    el('gearMinus1').onclick=()=>addOffset(-1);
    el('gearPlus1').onclick=()=>addOffset(1);
    el('gearPlus20').onclick=()=>addOffset(20);
    el('gearMinusStep').onclick=()=>addOffset(-selectedStep());
    el('gearPlusStep').onclick=()=>addOffset(selectedStep());
    el('gearToday').onclick=()=>{stopAuto();el('gearDate').value=dateValue(new Date());state.offset=0;renderMechanism();};
    el('gearPlay').onclick=toggleAuto;
    el('gearDate').onchange=()=>{state.offset=0;renderMechanism();};
    el('gearCorrelation').onchange=()=>{state.offset=0;renderMechanism();};
    el('gearSlider').oninput=e=>{state.offset=Number(e.target.value)||0;renderMechanism();};
    attachDrag(el('tzGear'),'tz');attachDrag(el('haabGear'),'haab');attachDrag(el('moonGear'),'moon');
    renderMechanism();
  }

  /* Override only the old Calendar Round page hooks. Global date and correlation remain untouched. */
  initGearControls=initMechanism;
  updateGearCalendar=renderMechanism;
})();
