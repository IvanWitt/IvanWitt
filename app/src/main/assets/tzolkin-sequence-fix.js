(function () {
  'use strict';

  // Chronological Tzolk'in order:
  // KIN 1 = 1 Imix’, KIN 2 = 2 Ik’, ... KIN 260 = 13 Ajaw.
  // The table keeps the existing 20 seal rows × 13 columns design.
  // Each column is one consecutive 20-day block, so reading top-to-bottom
  // and then left-to-right gives KIN 1 through KIN 260 without shuffling.
  function chronologicalToneForKin(kin) {
    return ((kin - 1) % 13) + 1;
  }

  window.buildTzolkin = function buildTzolkinChronological() {
    const table = document.getElementById('tzolkinTable');
    if (!table) return;
    table.innerHTML = '';

    const thead = document.createElement('thead');
    const hr = document.createElement('tr');
    const corner = document.createElement('th');
    corner.className = 'tz-tone-head tz-seal-head';
    corner.textContent = 'Печать';
    hr.appendChild(corner);

    for (let block = 0; block < 13; block++) {
      const th = document.createElement('th');
      th.className = 'tz-tone-head';
      th.innerHTML = `<span>${block + 1}</span>`;
      th.title = `КИН ${block * 20 + 1}–${(block + 1) * 20}`;
      hr.appendChild(th);
    }
    thead.appendChild(hr);

    const tbody = document.createElement('tbody');
    for (let signIndex = 0; signIndex < 20; signIndex++) {
      const tr = document.createElement('tr');
      const rowHead = document.createElement('th');
      rowHead.className = 'tz-seal-head';
      rowHead.innerHTML = `<div class="tz-seal-wrap">${sealGlyphSvg(signIndex)}<b>${dayNames[signIndex]}</b><small>${dayNamesRu[signIndex]}</small></div>`;
      tr.appendChild(rowHead);

      for (let block = 0; block < 13; block++) {
        const kin = block * 20 + signIndex + 1;
        const tone = chronologicalToneForKin(kin);
        const td = document.createElement('td');
        td.className = `tz-cell ${directionClasses[signIndex % 4]}`;
        td.dataset.kin = kin;
        td.dataset.tone = tone;
        td.dataset.signIndex = signIndex;
        td.title = `${tone} ${dayNames[signIndex]} (${dayNamesRu[signIndex]}), КИН ${kin}`;
        td.innerHTML = `<span class="kin-name">${tone} ${dayNames[signIndex]}</span><span class="kin-number">КИН ${kin}</span><span class="today-badge"></span>`;
        tr.appendChild(td);
      }
      tbody.appendChild(tr);
    }

    table.append(thead, tbody);
    if (typeof updateTzolkinHighlight === 'function' && window.current) {
      updateTzolkinHighlight();
    }
  };

  // Rebuild the already-rendered table after the main application script has loaded.
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => window.buildTzolkin(), { once: true });
  } else {
    window.buildTzolkin();
  }
})();
