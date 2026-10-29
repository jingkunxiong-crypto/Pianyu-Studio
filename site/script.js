const menu = document.querySelector('.menu-toggle');
const nav = document.querySelector('.site-nav');
document.querySelector('#year').textContent = new Date().getFullYear();
menu?.addEventListener('click', () => {
  const open = menu.getAttribute('aria-expanded') !== 'true';
  menu.setAttribute('aria-expanded', String(open));
  menu.setAttribute('aria-label', open ? '关闭导航' : '打开导航');
  nav.classList.toggle('is-open', open);
});
nav?.querySelectorAll('a').forEach(link => link.addEventListener('click', () => {
  menu.setAttribute('aria-expanded', 'false');
  menu.setAttribute('aria-label', '打开导航');
  nav.classList.remove('is-open');
}));

const preview = document.querySelector('#color-preview');
const reveal = document.querySelector('#color-reveal');
const hit = document.querySelector('#color-hit');
const range = document.querySelector('#mask-size');
const sizeValue = document.querySelector('#size-value');
const shapeButtons = document.querySelectorAll('.shape-button');
const mask = { x: 59, y: 43, size: Number(range.value), shape: 'circle' };
const pointers = new Map();
let pinchStart = null;
let previewRect = null;
let maskFrame = null;
const clamp = (value, low, high) => Math.max(low, Math.min(high, value));

function maskPolygon(points, cx, cy, radiusX, radiusY) {
  return 'polygon(' + points.map(([x,y]) => `${cx + x * radiusX}% ${cy + y * radiusY}%`).join(',') + ')';
}
function heartPoints() {
  const result = [];
  for (let i = 0; i < 72; i++) {
    const t = 2 * Math.PI * i / 72;
    const x = 16 * Math.sin(t) ** 3 / 18;
    const y = -(13 * Math.cos(t) - 5 * Math.cos(2*t) - 2 * Math.cos(3*t) - Math.cos(4*t)) / 18;
    result.push([x,y]);
  }
  return result;
}
const shapes = {
  star: Array.from({length:10},(_,i)=>{const angle=-Math.PI/2+i*Math.PI/5;const r=i%2?.44:1;return [Math.cos(angle)*r,Math.sin(angle)*r];}),
  diamond: [[0,-1],[1,0],[0,1],[-1,0]],
  heart: heartPoints()
};
function drawMask() {
  maskFrame = null;
  if (!preview || !reveal || !hit) return;
  const { x, y, size, shape } = mask;
  let clipPath;
  if (shape === 'circle') {
    clipPath = `circle(${size}% at ${x}% ${y}%)`;
  } else if (shape === 'rectangle' || shape === 'rounded') {
    const { width: w, height: h } = previewRect || (previewRect = preview.getBoundingClientRect());
    const halfW = w * size / 100, halfH = h * size * .72 / 100;
    const left = clamp(w*x/100-halfW,0,w), right=clamp(w-w*x/100-halfW,0,w);
    const top=clamp(h*y/100-halfH,0,h), bottom=clamp(h-h*y/100-halfH,0,h);
    clipPath = `inset(${top}px ${right}px ${bottom}px ${left}px${shape==='rounded'?' round 11%':''})`;
  } else {
    clipPath = maskPolygon(shapes[shape],x,y,size,size*.77);
  }
  reveal.style.clipPath = clipPath;
  hit.style.clipPath = clipPath;
  sizeValue.textContent = `${Math.round(size)}%`;
  range.value = String(Math.round(size));
}
function updateMask() {
  if (maskFrame === null) maskFrame = requestAnimationFrame(drawMask);
}
function moveTo(clientX,clientY) {
  const rect=previewRect || (previewRect = preview.getBoundingClientRect());
  mask.x=clamp((clientX-rect.left)/rect.width*100,0,100);
  mask.y=clamp((clientY-rect.top)/rect.height*100,0,100);
  updateMask();
}
hit?.addEventListener('pointerdown', event => {
  previewRect = preview.getBoundingClientRect();
  hit.setPointerCapture(event.pointerId);
  pointers.set(event.pointerId,{x:event.clientX,y:event.clientY});
  if(pointers.size===1) moveTo(event.clientX,event.clientY);
  if(pointers.size===2){const [a,b]=[...pointers.values()];pinchStart={distance:Math.hypot(a.x-b.x,a.y-b.y),size:mask.size};}
});
hit?.addEventListener('pointermove', event => {
  if(!pointers.has(event.pointerId)) return;
  pointers.set(event.pointerId,{x:event.clientX,y:event.clientY});
  if(pointers.size===1) moveTo(event.clientX,event.clientY);
  if(pointers.size===2 && pinchStart){const [a,b]=[...pointers.values()];const distance=Math.hypot(a.x-b.x,a.y-b.y);mask.size=clamp(pinchStart.size*distance/Math.max(1,pinchStart.distance),16,43);moveTo((a.x+b.x)/2,(a.y+b.y)/2);}
});
function endPointer(event){pointers.delete(event.pointerId);if(pointers.size<2)pinchStart=null;}
hit?.addEventListener('pointerup',endPointer);
hit?.addEventListener('pointercancel',endPointer);
preview?.addEventListener('keydown', event => {
  const step=event.shiftKey?10:3;
  if(event.key==='ArrowLeft')mask.x=clamp(mask.x-step,0,100);
  else if(event.key==='ArrowRight')mask.x=clamp(mask.x+step,0,100);
  else if(event.key==='ArrowUp')mask.y=clamp(mask.y-step,0,100);
  else if(event.key==='ArrowDown')mask.y=clamp(mask.y+step,0,100);
  else return;
  event.preventDefault();updateMask();
});
range?.addEventListener('input',()=>{mask.size=Number(range.value);updateMask();});
shapeButtons.forEach(button=>button.addEventListener('click',()=>{
  mask.shape=button.dataset.shape;
  shapeButtons.forEach(item=>{const active=item===button;item.classList.toggle('is-active',active);item.setAttribute('aria-pressed',String(active));});
  updateMask();
}));
window.addEventListener('resize',()=>{previewRect=null;updateMask();});
updateMask();

const pages = [...document.querySelectorAll('.page')];
const pageRail = document.querySelector('.page-rail');
const previousPage = pageRail?.querySelector('.page-rail-prev');
const nextPage = pageRail?.querySelector('.page-rail-next');
const pageNumber = pageRail?.querySelector('.page-rail-current b');
const pageTotal = pageRail?.querySelector('.page-rail-current span:last-child');
const pageLabel = pageRail?.querySelector('.page-rail-label');
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
const compactMotion = window.matchMedia('(max-width: 680px)');
const sceneSelectors = {
  top: ['.hero-title-line', '.hero-title-desc', '.hero-photo', '.hero-bottom p', '.hero-bottom a'],
  work: ['h2', 'p'],
  'selective-color': ['.work-heading h2', '.work-heading p', '.color-preview', '.color-details'],
  collage: ['.work-heading h2', '.work-heading p', '.collage-art', '.art-caption'],
  signature: ['.signature-copy h2', '.signature-copy p', '.signature-art'],
  palette: ['.palette-heading h2', '.palette-heading p', '.palette-art', '.art-caption'],
  about: ['.about-title h2', '.about-copy p', '.about-link'],
  download: ['h2', '.download-bottom p', '.download-meta', '.download-bottom a'],
  credits: ['.footer-wordmark', '.footer-note', '.footer-bottom']
};
const visualSelector = '.hero-photo,.color-preview,.collage-art,.signature-art,.palette-art';
let activePage = Math.max(0, pages.findIndex(page => `#${page.id}` === window.location.hash));
let turningPage = false;
let entryTimeline = null;
let wheelTotal = 0;
let wheelLastAt = 0;
let wheelConsumed = false;
let touchStart = null;

function sceneElements(page) {
  if (compactMotion.matches) {
    const selector = page.id === 'top' ? '.hero-title-line'
      : page.id === 'work' ? 'h2'
      : page.id === 'selective-color' || page.id === 'collage' ? '.work-heading h2'
      : page.id === 'signature' ? '.signature-copy h2'
      : page.id === 'palette' ? '.palette-heading h2'
      : page.id === 'about' ? '.about-title h2'
      : page.id === 'credits' ? '.footer-wordmark' : '.download-heading h2';
    return [...page.querySelectorAll(selector)];
  }
  return (sceneSelectors[page.id] || []).flatMap(selector => [...page.querySelectorAll(selector)]);
}

function prepareEntry(page) {
  if (!window.gsap || reducedMotion.matches) return;
  const elements = sceneElements(page);
  gsap.set(elements, { autoAlpha: 0, y: 20 });
  if (compactMotion.matches) return;
  const visual = page.querySelector(visualSelector);
  if (visual) gsap.set(visual, { scale: .985 });
  if (page.id === 'selective-color') gsap.set(reveal, { opacity: 0 });
  if (page.id === 'collage') gsap.set(page.querySelectorAll('.collage-cell'), { autoAlpha: 0, y: 20 });
  if (page.id === 'palette') gsap.set(page.querySelectorAll('.swatch'), { autoAlpha: 0, scale: .8 });
  if (page.id === 'signature') gsap.set(page.querySelector('.demo-signature'), { autoAlpha: 0, x: -18 });
  if (page.querySelector('.scene-art')) gsap.set(page.querySelector('.scene-art'), { opacity: 0 });
  if (page.id === 'download' || page.id === 'credits') gsap.set(page.querySelector('.ending-emblem'), { opacity: 0 });
}

function playEntry(page) {
  if (!window.gsap || reducedMotion.matches) return;
  const elements = sceneElements(page);
  const timeline = gsap.timeline({ defaults: { ease: 'power3.out' } });
  elements.forEach((element, index) => {
    const isVisual = element.matches(visualSelector);
    timeline.to(element, {
      autoAlpha: 1, y: 0, scale: 1, duration: isVisual ? .72 : .55,
      clearProps: 'transform,opacity,visibility'
    }, Math.min(index * .1, .36));
  });
  if (compactMotion.matches) {
    entryTimeline = timeline;
    return;
  }
  if (page.querySelector('.scene-art')) {
    timeline.to(page.querySelector('.scene-art'), {
      opacity: 1, duration: 1.05, ease: 'power2.out', clearProps: 'opacity'
    }, .08);
  }
  if (page.id === 'selective-color') {
    timeline.to(reveal, { opacity: 1, duration: .68, clearProps: 'opacity' }, .42);
  } else if (page.id === 'collage') {
    timeline.to(page.querySelectorAll('.collage-cell'), {
      autoAlpha: 1, y: 0, stagger: .08, duration: .52,
      clearProps: 'transform,opacity,visibility'
    }, .35);
  } else if (page.id === 'signature') {
    timeline.to(page.querySelector('.demo-signature'), {
      autoAlpha: 1, x: 0, duration: .72,
      clearProps: 'transform,opacity,visibility'
    }, .48);
  } else if (page.id === 'palette') {
    timeline.to(page.querySelectorAll('.swatch'), {
      autoAlpha: 1, scale: 1, stagger: .075, duration: .48,
      clearProps: 'transform,opacity,visibility'
    }, .43);
  }
  if (page.id === 'download' || page.id === 'credits') {
    timeline.to(page.querySelector('.ending-emblem'), {
      opacity: 1, duration: 1.05, ease: 'power2.out', clearProps: 'opacity'
    }, .12);
  }
  entryTimeline = timeline;
}

function warmPage(index) {
  pages[index]?.querySelectorAll('img[loading="lazy"]').forEach(img => { img.loading = 'eager'; });
}

function updateRail() {
  pageNumber.textContent = String(activePage + 1).padStart(2, '0');
  pageTotal.textContent = String(pages.length).padStart(2, '0');
  pageLabel.textContent = pages[activePage].dataset.pageLabel;
  previousPage.disabled = activePage === 0;
  nextPage.disabled = activePage === pages.length - 1;
  document.documentElement.dataset.pageTone = activePage >= pages.length - 2 ? 'dark' : 'light';
  warmPage(activePage + 1);
}

function finishTurn(current, incoming, index, updateAddress) {
  current.classList.remove('is-active');
  current.inert = true;
  current.setAttribute('aria-hidden', 'true');
  incoming.classList.remove('is-transitioning');
  incoming.classList.add('is-active');
  if (window.gsap) {
    gsap.set([current, incoming], { clearProps: 'transform,opacity,visibility' });
  }
  activePage = index;
  turningPage = false;
  updateRail();
  if (updateAddress) history.replaceState(null, '', `#${incoming.id}`);
}

function goToPage(index, updateAddress = true) {
  if (turningPage || index < 0 || index >= pages.length || index === activePage) return;
  const current = pages[activePage];
  const incoming = pages[index];
  const direction = index > activePage ? 1 : -1;
  turningPage = true;
  entryTimeline?.kill();
  if (window.gsap) gsap.set(sceneElements(current), { clearProps: 'transform,opacity,visibility' });
  incoming.inert = false;
  incoming.setAttribute('aria-hidden', 'false');
  incoming.classList.add('is-transitioning');
  warmPage(index);

  if (!window.gsap || reducedMotion.matches) {
    finishTurn(current, incoming, index, updateAddress);
    return;
  }

  prepareEntry(incoming);
  const mobile = compactMotion.matches;
  const duration = mobile ? .46 : .84;
  if (mobile) {
    gsap.set(current, { yPercent: 0 });
    gsap.set(incoming, { yPercent: direction * 103 });
    gsap.timeline({ onComplete: () => finishTurn(current, incoming, index, updateAddress) })
      .to(current, { yPercent: direction * -11, duration, ease: 'power2.out' }, 0)
      .to(incoming, { yPercent: 0, duration, ease: 'power2.out' }, 0)
      .add(() => playEntry(incoming), duration * .42);
    return;
  }
  gsap.set(current, { x: 0, y: 0, yPercent: 0, scale: 1, autoAlpha: 1 });
  gsap.set(incoming, { x: 0, y: 0, yPercent: direction * 105, scale: .965, autoAlpha: 1 });
  gsap.timeline({ onComplete: () => finishTurn(current, incoming, index, updateAddress) })
    .to(current, { yPercent: direction * -17, scale: .97, autoAlpha: .68, duration, ease: 'power2.out' }, 0)
    .to(incoming, { yPercent: 0, scale: 1, duration, ease: 'power2.out' }, 0)
    .add(() => playEntry(incoming), duration * .24);
}

if (pages.length && pageRail) {
  pages.forEach((page, index) => {
    const active = index === activePage;
    page.classList.toggle('is-active', active);
    page.inert = !active;
    page.setAttribute('aria-hidden', String(!active));
  });
  document.documentElement.classList.add('has-pages');
  warmPage(activePage);
  updateRail();
  if (window.location.hash && activePage > 0) {
    const emblem = pages[activePage].querySelector('.ending-emblem');
    if (emblem && window.gsap && !reducedMotion.matches) {
      gsap.fromTo(emblem, { opacity: 0 }, { opacity: 1, duration: .85, clearProps: 'opacity' });
    }
  } else {
    prepareEntry(pages[activePage]);
    requestAnimationFrame(() => playEntry(pages[activePage]));
  }

  previousPage.addEventListener('click', () => goToPage(activePage - 1));
  nextPage.addEventListener('click', () => goToPage(activePage + 1));
  document.querySelectorAll('a[href^="#"]').forEach(link => link.addEventListener('click', event => {
    const target = document.getElementById(link.hash.slice(1));
    const index = pages.indexOf(target);
    if (index < 0) return;
    event.preventDefault();
    goToPage(index);
  }));

  window.addEventListener('wheel', event => {
    if (event.ctrlKey || event.metaKey) return;
    event.preventDefault();
    const now = performance.now();
    if (now - wheelLastAt > 220) { wheelTotal = 0; wheelConsumed = false; }
    wheelLastAt = now;
    if (turningPage || wheelConsumed) return;
    wheelTotal += event.deltaY;
    if (Math.abs(wheelTotal) < 45) return;
    wheelConsumed = true;
    goToPage(activePage + Math.sign(wheelTotal));
  }, { passive: false });

  window.addEventListener('touchstart', event => {
    if (event.touches.length !== 1 || event.target.closest('a,button,input,.color-hit')) {
      touchStart = null;
      return;
    }
    touchStart = { x: event.touches[0].clientX, y: event.touches[0].clientY };
  }, { passive: true });
  window.addEventListener('touchend', event => {
    if (!touchStart || turningPage || !event.changedTouches.length) return;
    const dx = event.changedTouches[0].clientX - touchStart.x;
    const dy = event.changedTouches[0].clientY - touchStart.y;
    touchStart = null;
    if (Math.abs(dy) > 55 && Math.abs(dy) > Math.abs(dx) * 1.2) {
      goToPage(activePage + (dy < 0 ? 1 : -1));
    }
  }, { passive: true });
  window.addEventListener('touchcancel', () => { touchStart = null; }, { passive: true });

  window.addEventListener('keydown', event => {
    if (event.altKey || event.ctrlKey || event.metaKey || event.target.closest('input,button,#color-preview,[contenteditable="true"]')) return;
    const down = ['ArrowDown', 'PageDown', ' '].includes(event.key);
    const up = ['ArrowUp', 'PageUp'].includes(event.key) || (event.key === ' ' && event.shiftKey);
    if (down || up) {
      event.preventDefault();
      goToPage(activePage + (up ? -1 : 1));
    } else if (event.key === 'Home') {
      event.preventDefault();
      goToPage(0);
    } else if (event.key === 'End') {
      event.preventDefault();
      goToPage(pages.length - 1);
    }
  });
  window.addEventListener('hashchange', () => {
    const index = pages.findIndex(page => `#${page.id}` === window.location.hash);
    if (index >= 0) goToPage(index, false);
  });
}
