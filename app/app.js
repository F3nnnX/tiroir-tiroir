'use strict';
/* Tiroir Tiroir : version web de démonstration. Port fidèle de Compteur/Item/Donnees/Principal (Java).
   Données : localStorage uniquement (rien ne quitte l'appareil). */
const RIEN = 2147483647, CLE = 'tiroir-tiroir-demo-v1';

// ---------- dates (jours depuis 1970, comme LocalDate.toEpochDay) ----------
function auj() { const n = new Date(); return Math.round(Date.UTC(n.getFullYear(), n.getMonth(), n.getDate()) / 86400000); }
function dt(ed) { return new Date(ed * 86400000); }               // lire avec getUTC*
function p2(n) { return (n < 10 ? '0' : '') + n; }
const MOIS = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'];
// « 28 octobre » ; l'année (« 28 octobre 2027 ») seulement si ce n'est pas l'année en cours. Le stockage ne change pas.
function jm(ed, a) { const d = dt(ed); return d.getUTCDate() + ' ' + MOIS[d.getUTCMonth()] + (d.getUTCFullYear() === dt(a).getUTCFullYear() ? '' : ' ' + d.getUTCFullYear()); }
function fmt(ed) { return jm(ed, auj()); }
function iso(ed) { const d = dt(ed); return d.getUTCFullYear() + '-' + p2(d.getUTCMonth() + 1) + '-' + p2(d.getUTCDate()); }
function deIso(s) { const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s || ''); return m ? Math.round(Date.UTC(+m[1], +m[2] - 1, +m[3]) / 86400000) : 0; }
function lgMois(y, m) { return new Date(Date.UTC(y, m + 1, 0)).getUTCDate(); }
function plusMois(ed, n) {            // LocalDate.plusMonths (jour ramené à la fin du mois si besoin)
  const d = dt(ed), t = d.getUTCFullYear() * 12 + d.getUTCMonth() + n, y = Math.floor(t / 12), m = t - y * 12;
  return Math.round(Date.UTC(y, m, Math.min(d.getUTCDate(), lgMois(y, m))) / 86400000);
}
function jourDuMois(ed) { return dt(ed).getUTCDate(); }

// ---------- textes ----------
function nb(n) { const s = String(Math.abs(n)); let r = ''; for (let i = 0; i < s.length; i++) { if (i > 0 && (s.length - i) % 3 === 0) r += ' '; r += s[i]; } return (n < 0 ? '-' : '') + r; }
function pl(n, mot) { return nb(n) + ' ' + mot + (n > 1 ? 's' : ''); }
function cap(s) { return s ? s[0].toUpperCase() + s.slice(1) : s; }
function num(s) { s = String(s || '').replace(/[^0-9]/g, ''); if (s.length > 12) s = s.slice(0, 12); return s ? parseInt(s, 10) : 0; }

// ---------- données ----------
let D = null;
function sauver() { try { localStorage.setItem(CLE, JSON.stringify(D)); } catch (e) { /* stockage plein ou refusé */ } }
function charger() {
  try { const t = localStorage.getItem(CLE); if (t) { const o = JSON.parse(t); if (o && o.dossiers && o.items) { D = o; return; } } } catch (e) { /* corrompu : on repart */ }
  D = { suivant: 1, dossiers: [], items: [] };
  ['Maison', 'Travail', 'Voiture', 'Autre'].forEach(n => ajoutDossier(0, n));
  exemples(); sauver();
}
function ajoutDossier(parent, nom, ex) {
  const x = { id: D.suivant++, parent, nom, vehicule: false, kmActuel: 0, kmSem: 0, kmDate: auj(), qDate: auj() };
  if (ex) x.ex = true; D.dossiers.push(x); return x;
}
function nouvelItem(dossier, type) {
  const i = { id: D.suivant++, dossier, type, nom: '', designation: '', lien: '', niveaux: '30,15,7,2,0',
    periode: 3, jour: 1, mois: true, ref: 0, prec: 0, montant: 0, intervalle: 10000, kmDernier: 0,
    kmActuel: 0, kmSem: 0, kmDate: auj(), qDate: auj(), s1: 1000, s2: 300, question: true, envoye: RIEN };
  if (type !== 'K') { const r = auj() + 30; i.ref = r; i.jour = jourDuMois(r); }
  return i;
}
function dossier(id) { return D.dossiers.find(x => x.id === id) || null; }
function item(id) { return D.items.find(x => x.id === id) || null; }
function enfants(p) { return D.dossiers.filter(x => x.parent === p); }
function itemsDe(d) { return D.items.filter(x => x.dossier === d); }
function dans(id, rac) { for (let x = dossier(id); x; x = dossier(x.parent)) if (x.id === rac) return true; return false; }
function sousArbre(rac) { return D.items.filter(x => rac === 0 || dans(x.dossier, rac)); }
function chemin(id) { let r = ''; for (let x = dossier(id); x; x = dossier(x.parent)) r = x.nom + (r ? ' › ' + r : ''); return r; }
function vehiculeDe(id) { for (let x = dossier(id); x; x = dossier(x.parent)) if (x.vehicule) return x; return null; }
function supprimerDossier(id) {
  D.items = D.items.filter(x => !dans(x.dossier, id));
  D.dossiers = D.dossiers.filter(x => !dans(x.id, id)); sauver();
}
function sousVoiture(x) { for (let y = x; y; y = dossier(y.parent)) if (y.parent === 0) return y.nom.toLowerCase() === 'voiture'; return false; }

function exemples() {
  const racine = n => D.dossiers.find(x => x.parent === 0 && x.nom === n) || ajoutDossier(0, n);
  const maison = racine('Maison'), travail = racine('Travail'), voiture = racine('Voiture');
  const fact = ajoutDossier(maison.id, 'Factures', true); ajoutDossier(maison.id, 'Entretien', true);
  const a = auj(), p = plusMois(a, 1) - jourDuMois(plusMois(a, 1)) + 1;
  const e = (d, nom, o) => { const i = nouvelItem(d.id, 'E'); i.nom = nom; i.ex = true; Object.assign(i, o); i.jour = jourDuMois(i.ref); D.items.push(i); return i; };
  e(fact, 'Sonelgaz', { montant: 20000, periode: 3, ref: p });
  e(fact, 'Eau', { periode: 3, ref: a + 11 });
  e(maison, 'Loyer', { designation: 'le loyer', montant: 45000, periode: 1, ref: a + 1 });
  e(travail, 'Agrément sanitaire', { designation: "l'agrément sanitaire", periode: 6, ref: a + 75 });
  const v = ajoutDossier(voiture.id, 'Ma voiture', true); v.vehicule = true; v.kmActuel = 48200; v.kmSem = 220; v.kmDate = a - 21; v.qDate = a;
  const k = (nom, o) => { const i = nouvelItem(v.id, 'K'); i.nom = nom; i.ex = true; Object.assign(i, o); D.items.push(i); };
  e(v, 'Assurance auto', { montant: 32000, periode: 6, ref: a - 3 });
  k('Vidange', { intervalle: 10000, kmDernier: 39800 });
  k('Plaquettes de frein', { intervalle: 30000, kmDernier: 20000 });
}
function effacerExemples() {
  D.items = D.items.filter(x => !x.ex);
  let change = true;
  while (change) {
    change = false;
    D.dossiers = D.dossiers.filter(x => {
      const vide = x.ex && !D.items.some(i => i.dossier === x.id) && !D.dossiers.some(y => y.parent === x.id);
      if (vide) change = true; return !vide;
    });
  }
  sauver();
}
const aDesExemples = () => D.items.some(x => x.ex) || D.dossiers.some(x => x.ex);

// ---------- Compteur ----------
function actuelC(c, a) { return c.kmSem <= 0 ? c.kmActuel : c.kmActuel + Math.floor(c.kmSem * Math.max(0, a - c.kmDate) / 7); }
function releve(c, km, a) {
  const j = a - c.kmDate; let info = null;
  if (c.kmSem > 0 && j >= 7 && km > c.kmActuel) {
    const nv = Math.round((km - c.kmActuel) * 7 / j);
    if (nv > 0 && j >= 14) { info = 'Moyenne mise à jour : ≈ ' + nb(nv) + ' km par semaine (avant : ' + nb(c.kmSem) + ').'; c.kmSem = nv; }
    else if (nv > 0) info = 'Depuis le dernier relevé, tu roules ≈ ' + nb(nv) + ' km par semaine (moyenne actuelle : ' + nb(c.kmSem) + '). Modifie-la si tu veux.';
  }
  c.kmActuel = km; c.kmDate = a; c.qDate = a; return info;
}

// ---------- Item ----------
const estK = it => it.type === 'K';
const cpt = it => vehiculeDe(it.dossier) || it;       // porteur des données km (véhicule ou l'élément lui-même)
const estime = it => estK(it) && cpt(it).kmSem > 0;
const actuel = (it, a) => actuelC(cpt(it), a);
const reste = (it, a) => it.kmDernier + it.intervalle - actuel(it, a);
const jours = (it, a) => it.ref - a;
function vers(it, a) {
  const r = reste(it, a); if (!estime(it) || r <= 0) return null;
  const ks = cpt(it).kmSem; return a + Math.floor((r * 7 + ks - 1) / ks);
}
const retard = (it, a) => estK(it) ? reste(it, a) <= 0 : jours(it, a) < 0;
function lesNiveaux(it) { return it.niveaux.split(',').map(s => s.trim()).filter(s => s).map(Number); }
const aNiveau = (it, l) => lesNiveaux(it).includes(l);
function niveau(it, a) {
  if (estK(it)) { const r = reste(it, a); return r <= 0 ? 0 : r <= it.s2 ? it.s2 : r <= it.s1 ? it.s1 : RIEN; }
  const d = jours(it, a); if (d < 0) return -1;
  let best = RIEN; for (const l of lesNiveaux(it)) if (l >= d && l < best) best = l; return best;
}
const urgent = (it, niv) => estK(it) ? niv <= it.s2 : niv <= 2;
function suivant(it) {
  if (!it.mois) return it.ref + it.periode;
  const d = plusMois(it.ref, it.periode), x = dt(d);
  return d - x.getUTCDate() + Math.min(it.jour, lgMois(x.getUTCFullYear(), x.getUTCMonth())) ;
}
function paye(it) { if (estK(it)) it.kmDernier = actuel(it, auj()); else { it.prec = it.ref; it.ref = suivant(it); } it.envoye = RIEN; }
function score(it, a) { if (!estK(it)) return jours(it, a); return estime(it) ? reste(it, a) * 7 / cpt(it).kmSem : reste(it, a) / 50; }
function statut(it, a) {
  if (estK(it)) {
    const r = reste(it, a);
    if (r < 0) return 'Dépassé de ' + nb(-r) + (estime(it) ? ' km (estimé)' : ' km') + ' : à faire !';
    if (r === 0) return 'Kilométrage atteint : à faire !';
    const v = vers(it, a);
    return 'Dans ' + (estime(it) ? '≈ ' : '') + nb(r) + ' km (à ' + nb(it.kmDernier + it.intervalle) + ' km)' + (v === null ? '' : ' · vers le ' + jm(v, a));
  }
  const d = jours(it, a), s = ' (' + fmt(it.ref) + ')', m = it.montant > 0 ? ' · ' + nb(it.montant) + ' DA' : '';
  if (d < 0) return 'EN RETARD de ' + pl(-d, 'jour') + s + m;
  if (d === 0) return "Échéance aujourd'hui" + m;
  if (d === 1) return 'Demain' + s + m;
  return 'Dans ' + nb(d) + ' jours' + s + m;
}
// ---------- liste « À venir » ----------
function quand(it, a) { if (!estK(it)) return it.ref; if (reste(it, a) <= 0) return a; return vers(it, a); }
function urgence(it, a) {       // 2 = en retard (rouge), 1 = proche (orange), 0 = pas pressé
  if (retard(it, a)) return 2;
  if (!estK(it)) return jours(it, a) <= 14 ? 1 : 0;
  const v = vers(it, a);
  return reste(it, a) <= it.s1 || (v !== null && v - a <= 14) ? 1 : 0;
}
function ligne(it, a) {         // [date, action] : « 28 octobre : Agrément Sanitaire » ; km : « date estimée : action – Véhicule »
  const veh = vehiculeDe(it.dossier), suf = veh && estK(it) ? ' – ' + veh.nom : '';
  if (estK(it) && retard(it, a)) return ['maintenant', it.nom + suf];
  const q = quand(it, a);
  if (q === null) return ['à ' + nb(it.kmDernier + it.intervalle) + ' km', it.nom + suf];
  return [jm(q, a), it.nom + suf];
}
function couleur(it, a) { if (retard(it, a)) return 'rouge'; const n = niveau(it, a); return n !== RIEN && urgent(it, n) ? 'orange' : ''; }
function resume(id) {
  const l = sousArbre(id), a = auj(), r = l.filter(x => retard(x, a)).length;
  return l.length === 0 ? 'Vide' : pl(l.length, 'élément') + (r > 0 ? ' · ' + r + ' en retard' : '');
}
const couleurDossier = id => sousArbre(id).some(x => retard(x, auj())) ? 'rouge' : '';
function infoVehicule(v, a) {
  return v.kmSem > 0 ? '≈ ' + nb(actuelC(v, a)) + ' km (estimé, relevé : ' + nb(v.kmActuel) + ' km le ' + jm(v.kmDate, a) + ') · ≈ ' + nb(v.kmSem) + ' km/semaine'
    : nb(v.kmActuel) + ' km (relevé le ' + jm(v.kmDate, a) + ')';
}
function rappels(it) {
  const r = lesNiveaux(it).map(l => l === 0 ? 'jour J' : 'J-' + l).join(', ');
  return r || 'aucun (seulement le retard)';
}

// ---------- DOM ----------
function h(tag, attrs, ...kids) {
  const e = document.createElement(tag);
  for (const k in (attrs || {})) {
    if (k === 'class') e.className = attrs[k]; else if (k.startsWith('on')) e.addEventListener(k.slice(2), attrs[k]);
    else if (attrs[k] !== false && attrs[k] != null) e.setAttribute(k, attrs[k] === true ? '' : attrs[k]);
  }
  for (const c of kids.flat()) if (c != null && c !== false) e.append(c.nodeType ? c : document.createTextNode(c));
  return e;
}
const APP = () => document.getElementById('app');
let tt = null;
function toast(s) { const t = document.getElementById('toast'); t.textContent = s; t.hidden = false; clearTimeout(tt); tt = setTimeout(() => t.hidden = true, 6000); }
function fermer() { const m = document.getElementById('modal'); m.hidden = true; m.replaceChildren(); }
function modal(...kids) { const m = document.getElementById('modal'); m.replaceChildren(h('div', { class: 'b' }, ...kids)); m.hidden = false; }
function confirmer(msg, oui) {
  modal(h('p', {}, msg), h('div', { class: 'acts' }, h('button', { onclick: fermer }, 'Non'), h('button', { onclick: () => { fermer(); oui(); } }, 'Oui')));
}
function saisie(titre, init, ok) {
  const e = h('input', { type: 'text', value: init, autocapitalize: 'sentences' });
  const go = () => { const s = e.value.trim(); fermer(); if (s) ok(s); };
  e.addEventListener('keydown', ev => { if (ev.key === 'Enter') go(); });
  modal(h('h2', {}, titre), e, h('div', { class: 'acts' }, h('button', { onclick: fermer }, 'Annuler'), h('button', { onclick: go }, 'OK')));
  setTimeout(() => e.focus(), 50);
}
function choix(titre, libs, cb) {
  modal(h('h2', {}, titre), ...libs.map((l, i) => h('button', { class: 'poignee', onclick: () => { fermer(); cb(i); } }, l)),
    h('div', { class: 'acts' }, h('button', { onclick: fermer }, 'Annuler')));
}
document.getElementById('modal').addEventListener('click', e => { if (e.target.id === 'modal') fermer(); });

const btn = (s, cl, f) => h('button', { class: cl || '', onclick: f }, s);
function tiroir(titre, sous, cl, clic, long) {
  const e = h('div', { class: 'tiroir ' + (cl || ''), role: 'button', tabindex: 0 },
    h('div', { class: 'n' }, titre), sous ? h('div', { class: 's' }, sous) : null, h('div', { class: 'h' }));
  let timer = null, fait = false;
  const stop = () => clearTimeout(timer);
  if (long) {
    e.addEventListener('pointerdown', () => { fait = false; timer = setTimeout(() => { fait = true; long(); }, 550); });
    ['pointerup', 'pointerleave', 'pointercancel', 'pointermove'].forEach(n => e.addEventListener(n, stop));
    e.addEventListener('contextmenu', ev => { ev.preventDefault(); stop(); if (!fait) { fait = true; long(); } });
  }
  e.addEventListener('click', () => { if (fait) { fait = false; return; } clic(); });
  e.addEventListener('keydown', ev => { if (ev.key === 'Enter') clic(); });
  return e;
}

// ---------- navigation (pile + bouton Retour du navigateur) ----------
const pile = [];
function montrer() { fermer(); pile[pile.length - 1](); window.scrollTo(0, 0); }
function aller(f) { pile.push(f); history.pushState({ i: pile.length }, ''); montrer(); }
function retour(n) { n = n || 1; if (pile.length <= n) return; history.go(-n); }
function rafraichir() { const y = window.scrollY; fermer(); pile[pile.length - 1](); window.scrollTo(0, y); }
window.addEventListener('popstate', e => {
  const i = (e.state && e.state.i) || 1;
  while (pile.length > i) pile.pop();
  if (pile.length) montrer();
});

function page(titre, avecRetour) {
  const p = APP(); p.replaceChildren(); document.documentElement.classList.remove('epure');
  if (avecRetour) p.append(btn('‹ Retour', 'poignee', () => retour(1)));
  p.append(h('h1', {}, titre));
  return p;
}
function echoDate(inp) {      // la date choisie, écrite « 28 octobre », sous le sélecteur du navigateur
  const e = h('p', { class: 'echo' });
  const maj = () => { const d = deIso(inp.value); e.textContent = d ? jm(d, auj()) : ''; };
  inp.addEventListener('input', maj); inp.addEventListener('change', maj); maj(); return e;
}
function champ(p, lab, val, type, extra) {
  p.append(h('label', { class: 'lab' }, lab));
  const e = h('input', Object.assign({ type: type === 'num' ? 'text' : type || 'text', value: val == null ? '' : val }, type === 'num' ? { inputmode: 'numeric', pattern: '[0-9]*' } : { autocapitalize: type === 'url' ? 'none' : 'sentences' }, extra || {}));
  p.append(e); return e;
}
function caseCoche(lab, coche) {
  const c = h('input', { type: 'checkbox' }); c.checked = coche;
  return [h('label', { class: 'case' }, c, lab), c];
}

// ---------- écrans ----------
function accueil() {
  const p = page('Tiroir Tiroir', false), a = auj();
  const proches = D.items.filter(x => couleur(x, a)).sort((x, y) => score(x, a) - score(y, a));
  if (proches.length) {
    p.append(h('p', { class: 't', style: 'font-weight:bold;margin-top:14px' }, 'À surveiller'));
    proches.forEach(it => p.append(tiroir(it.nom, chemin(it.dossier) + '\n' + statut(it, a), couleur(it, a), () => aller(() => detail(it.id)))));
  }
  enfants(0).forEach(r => p.append(tiroir(r.nom, resume(r.id), couleurDossier(r.id), () => aller(() => dossierEcran(r.id)))));
  p.append(btn('À venir', 'vert', () => aller(() => avenir(0))));
  if (aDesExemples()) p.append(btn('Effacer les exemples', 'poignee', () => confirmer('Effacer tous les exemples fictifs ? Ce que vous avez ajouté vous-même est conservé.', () => { effacerExemples(); rafraichir(); toast('Exemples effacés.'); })));
  else p.append(btn('Remettre les exemples', 'poignee', () => { exemples(); sauver(); rafraichir(); }));
  p.append(h('p', { class: 'petit' }, 'Les données restent sur cet appareil (navigateur). Pour une vraie surveillance avec alarmes, utilisez l\'appli Android.'));
}

function dossierEcran(id) {
  const cur = dossier(id); if (!cur) { retour(1); return; }
  const p = page(chemin(id), true), a = auj();
  const voitures = cur.parent === 0 && cur.nom.toLowerCase() === 'voiture';
  if (cur.vehicule) p.append(h('p', { class: 't' }, infoVehicule(cur, a)));
  // Niveau « Voiture » : les véhicules seulement (d'anciens éléments / sous-tiroirs éventuels restent visibles en dessous).
  const sous = s => tiroir(s.nom, (s.vehicule ? infoVehicule(s, a) + '\n' : '') + resume(s.id), couleurDossier(s.id), () => aller(() => dossierEcran(s.id)), () => actionsDossier(s.id));
  enfants(id).filter(s => !voitures || s.vehicule).forEach(s => p.append(sous(s)));
  if (voitures) enfants(id).filter(s => !s.vehicule).forEach(s => p.append(sous(s)));
  itemsDe(id).forEach(it => p.append(tiroir(it.nom, statut(it, a), couleur(it, a), () => aller(() => detail(it.id)))));
  if (voitures) {
    if (!enfants(id).length) p.append(h('p', { class: 't' }, "Aucun véhicule pour l'instant."));
    if (sousArbre(id).length) p.append(btn('À venir dans ce tiroir', 'vert', () => aller(() => avenir(id))));
    p.append(btn('+ Ajouter un véhicule', '', () => aller(() => formVehicule(0, id))));
    return;
  }
  if (!enfants(id).length && !itemsDe(id).length) p.append(h('p', { class: 't' }, 'Ce tiroir est vide.'));
  const types = ['Échéance (facture, assurance, contrôle...)', 'Kilométrage (vidange, distribution...)'];
  const dansVehicule = !!vehiculeDe(id);
  p.append(btn('+ Ajouter un élément', 'poignee', () => choix("Quel type d'élément ?",
    dansVehicule ? types : types.concat(['Un sous-tiroir (pour regrouper)']),
    w => { if (w === 2) saisie('Nom du sous-tiroir', '', n => { ajoutDossier(id, n); sauver(); rafraichir(); }); else aller(() => formulaire(0, id, w === 0 ? 'E' : 'K')); })));
  if (sousArbre(id).length) p.append(btn('À venir dans ce tiroir', 'vert', () => aller(() => avenir(id))));
  if (cur.vehicule) p.append(h('a', { class: 'reglages', role: 'button', tabindex: 0, onclick: () => aller(() => formVehicule(id, cur.parent)) }, 'Réglages du véhicule'));
  else if (cur.parent !== 0) p.append(h('p', { class: 'petit' }, 'Astuce : appui long sur un sous-tiroir pour le renommer ou le supprimer.'));
}

function actionsDossier(id) {
  const x = dossier(id);
  choix(x.nom, ['Renommer', 'Supprimer', x.vehicule ? 'Modifier le véhicule (km)' : 'En faire un véhicule (km partagés)'], w => {
    if (w === 2) { if (!x.vehicule) { x.vehicule = true; sauver(); } aller(() => formVehicule(x.id, x.parent)); }
    else if (w === 0) saisie('Nouveau nom', x.nom, n => { x.nom = n; sauver(); rafraichir(); });
    else confirmer('Supprimer « ' + x.nom + ' » avec tout son contenu ?', () => { supprimerDossier(id); rafraichir(); });
  });
}

function formVehicule(id, parent) {
  const ex = id === 0 ? null : dossier(id);
  if (id !== 0 && !ex) { retour(1); return; }
  const p = page(ex ? 'Véhicule : ' + ex.nom : 'Nouveau véhicule', true);
  const k = ex || { kmActuel: 0, kmSem: 0 };
  const nom = champ(p, 'Nom du véhicule (ex. Peugeot 308)', ex ? ex.nom : '', 'text');
  const ka = champ(p, ex && k.kmSem > 0 ? 'Dernier kilométrage relevé (réel)' : 'Kilométrage actuel du compteur', ex ? k.kmActuel : '', 'num');
  const ks = champ(p, 'Km roulés par semaine (approximatif, facultatif)', k.kmSem > 0 ? k.kmSem : '', 'num');
  p.append(h('p', { class: 'petit' }, "Si tu le renseignes, l'appli estime le kilométrage toute seule et ne te le demande que toutes les 4 semaines. Vide ou 0 : pas d'estimation. Ces données servent à tous les éléments km de ce véhicule."));
  p.append(btn('Enregistrer', 'vert', () => {
    const n = nom.value.trim(); if (!n) { toast('Donne un nom au véhicule.'); return; }
    const nka = num(ka.value), nks = Math.min(num(ks.value), 100000);
    const x = ex || ajoutDossier(parent, n);
    x.nom = n; x.vehicule = true;
    if (!ex || nka !== x.kmActuel || (x.kmSem === 0 && nks > 0)) x.kmDate = auj();
    x.kmActuel = nka; x.kmSem = nks;
    if (!ex) x.qDate = auj();
    sousArbre(x.id).forEach(i => i.envoye = RIEN);
    sauver(); retour(1);
  }));
  if (ex) p.append(btn('Supprimer ce véhicule', 'rouge', () => confirmer('Supprimer « ' + ex.nom + ' » avec tous ses éléments ?', () => { supprimerDossier(ex.id); retour(1); })));
}

function avenir(rac) {
  const r = dossier(rac), p = page(r ? 'À venir : ' + r.nom : 'À venir', true), a = auj();
  document.documentElement.classList.add('epure');
  const l = sousArbre(rac).sort((x, y) => score(x, a) - score(y, a));
  if (!l.length) p.append(h('p', { class: 't' }, 'Rien à venir. Ajoutez des éléments dans les tiroirs.'));
  l.forEach(it => {
    const u = urgence(it, a), [d, act] = ligne(it, a);
    p.append(h('div', { class: 'lig u' + u, role: 'button', tabindex: 0, onclick: () => aller(() => detail(it.id)) },
      h('span', { class: 'd' }, d), ' : ', h('b', {}, act)));
  });
}

function detail(id) {
  const it = item(id); if (!it) { retour(1); return; }
  const a = auj(), p = page(it.nom, true), k = cpt(it), veh = vehiculeDe(it.dossier);
  p.append(h('p', { class: 't', style: 'font-size:16px' }, chemin(it.dossier)));
  p.append(h('div', { class: 'statut ' + couleur(it, a) }, statut(it, a)));
  let s;
  if (estK(it)) {
    s = (veh ? 'Véhicule : ' + veh.nom + '\n' : '') + 'Intervalle : ' + nb(it.intervalle) + ' km\n' + 'Dernier entretien à : ' + nb(it.kmDernier) + ' km\n'
      + (estime(it) ? 'Kilométrage : ≈ ' + nb(actuel(it, a)) + ' km (estimé)\nDernier relevé réel : ' + nb(k.kmActuel) + ' km le ' + jm(k.kmDate, a) + '\nRoule environ ' + nb(k.kmSem) + ' km par semaine\n'
        : 'Kilométrage actuel : ' + nb(k.kmActuel) + ' km\n')
      + 'Prochain à : ' + nb(it.kmDernier + it.intervalle) + ' km\n' + 'Alertes à ' + nb(it.s1) + ' et ' + nb(it.s2) + ' km\n'
      + (it.question ? 'Question chaque semaine : oui' : 'Question chaque semaine : non');
  } else {
    s = 'Revient tous les ' + it.periode + (it.mois ? ' mois' : ' jours') + '\n' + 'Prochaine échéance : ' + fmt(it.ref) + '\n'
      + (it.prec ? 'Précédente : ' + fmt(it.prec) + '\n' : '') + (it.montant > 0 ? 'Montant estimé : ' + nb(it.montant) + ' DA\n' : '')
      + 'Rappels : ' + rappels(it) + (it.lien ? '\nLien : ' + it.lien : '');
  }
  p.append(h('p', { class: 't' }, s));
  p.append(btn(estK(it) ? 'Fait' : 'Payé / Fait', 'vert', () => {
    const suite = estK(it) ? 'Le prochain sera à ' + nb(actuel(it, a) + it.intervalle) + ' km.' : 'La prochaine échéance sera le ' + fmt(suivant(it)) + '.';
    confirmer('Marquer « ' + it.nom + ' » comme ' + (estK(it) ? 'fait' : 'payé') + ' ? ' + suite, () => { paye(it); sauver(); rafraichir(); });
  }));
  if (!estK(it) && it.lien) p.append(btn('Payer en ligne (navigateur)', '', () => window.open(it.lien.includes('://') ? it.lien : 'https://' + it.lien, '_blank', 'noopener')));
  if (estK(it)) {
    const km = champ(p, estime(it) ? "Vrai kilométrage du compteur (remplace l'estimation" + (veh ? ', met à jour tout le véhicule)' : ')')
      : 'Kilométrage actuel' + (veh ? ' (met à jour tout le véhicule)' : ''), estime(it) ? '' : k.kmActuel, 'num');
    p.append(btn('Enregistrer le kilométrage', '', () => {
      if (!km.value.trim()) { toast('Tape le kilométrage du compteur.'); return; }
      const info = releve(k, num(km.value), auj()); sauver();
      if (info) toast(info); rafraichir();
    }));
  }
  p.append(btn('Modifier', 'poignee', () => aller(() => formulaire(id, it.dossier, it.type))));
}

function formulaire(itemId, dossierId, type) {
  const ex = item(itemId), it = ex || nouvelItem(dossierId, type), km = estK(it), veh = vehiculeDe(it.dossier);
  const p = page(ex ? 'Modifier' : 'Nouvel élément', true);
  const nom = champ(p, 'Nom (ex. ' + (km ? 'Vidange' : 'Sonelgaz') + ')', it.nom, 'text');
  let mont, per, lien, interv, kd, ka, ks, s1, s2, q, mois = it.mois, cb = [], prec, ref;
  const niv = [30, 15, 7, 2, 0];
  if (!km) {
    mont = champ(p, 'Montant estimé en DA (facultatif)', it.montant > 0 ? it.montant : '', 'num');
    p.append(h('label', { class: 'lab' }, 'Revient tous les'));
    per = h('input', { type: 'text', inputmode: 'numeric', value: it.periode });
    const u = btn(mois ? 'mois' : 'jours', 'poignee', () => { mois = !mois; u.textContent = mois ? 'mois' : 'jours'; });
    p.append(h('div', { class: 'ligne' }, per, u));
    p.append(h('label', { class: 'lab' }, 'Date de la précédente échéance (facultatif)'));
    prec = h('input', { type: 'date', value: it.prec ? iso(it.prec) : '' }); p.append(prec, echoDate(prec));
    p.append(h('label', { class: 'lab' }, 'Date de la prochaine échéance (date de référence)'));
    ref = h('input', { type: 'date', value: it.ref ? iso(it.ref) : '' }); p.append(ref, echoDate(ref));
    lien = champ(p, 'Lien de paiement en ligne (facultatif)', it.lien, 'url');
    p.append(h('p', { class: 't', style: 'font-weight:bold' }, 'Me rappeler :'));
    ['30 jours avant', '15 jours avant', '7 jours avant', '2 jours avant', 'Le jour même'].forEach((l, i) => {
      const [el, c] = caseCoche(l, aNiveau(it, niv[i])); cb.push(c); p.append(el);
    });
  } else {
    interv = champ(p, 'Tous les combien de km ? (ex. 10000)', it.intervalle, 'num');
    kd = champ(p, 'Kilométrage au dernier entretien', it.kmDernier, 'num');
    if (!veh) {
      ka = champ(p, estime(it) ? 'Dernier kilométrage relevé (réel)' : 'Kilométrage actuel', it.kmActuel, 'num');
      ks = champ(p, 'Km roulés par semaine (approximatif, facultatif)', it.kmSem > 0 ? it.kmSem : '', 'num');
      p.append(h('p', { class: 'petit' }, "Si tu le renseignes, l'appli estime ton kilométrage toute seule et ne te le demande que toutes les 4 semaines. Vide ou 0 : pas d'estimation."));
    } else p.append(h('p', { class: 'petit' }, 'Le kilométrage actuel et la moyenne par semaine se règlent sur le véhicule « ' + veh.nom + ' ».'));
    s1 = champ(p, 'Première alerte quand il reste (km)', it.s1, 'num');
    s2 = champ(p, 'Alerte urgente quand il reste (km)', it.s2, 'num');
    const [el, c] = caseCoche('Me demander le kilométrage chaque semaine', it.question); q = c; p.append(el);
  }
  p.append(btn('Enregistrer', 'vert', () => {
    const n = nom.value.trim(); if (!n) { toast('Donnez un nom à cet élément.'); return; }
    if (!km) {
      const pe = num(per.value); if (pe < 1 || pe > 1200) { toast('La période doit être d\'au moins 1.'); return; }
      const r = deIso(ref.value); if (!r) { toast("Choisissez la date de la prochaine échéance."); return; }
      it.periode = pe; it.mois = mois;
      if (r !== it.ref) it.jour = jourDuMois(r);
      it.ref = r; it.prec = deIso(prec.value);
      it.montant = num(mont.value); it.lien = lien.value.trim();
      it.niveaux = niv.filter((_, i) => cb[i].checked).join(',');
    } else {
      if (num(interv.value) < 1) { toast("L'intervalle doit être d'au moins 1 km."); return; }
      it.intervalle = num(interv.value); it.kmDernier = num(kd.value);
      if (ka) {
        const nka = num(ka.value), nks = Math.min(num(ks.value), 100000);
        if (nka !== it.kmActuel || (it.kmSem === 0 && nks > 0)) it.kmDate = auj();
        it.kmActuel = nka; it.kmSem = nks;
      }
      it.s1 = Math.min(num(s1.value), 1000000); it.s2 = Math.min(num(s2.value), 1000000); it.question = q.checked;
    }
    it.nom = n; it.envoye = RIEN;
    if (!ex) D.items.push(it);
    sauver(); retour(1);
  }));
  if (ex) p.append(btn('Supprimer cet élément', 'rouge', () => confirmer('Supprimer « ' + ex.nom + ' » ?', () => {
    D.items = D.items.filter(x => x !== ex); sauver(); retour(2);
  })));
}

// ---------- démarrage ----------
charger();
pile.push(accueil);
history.replaceState({ i: 1 }, '');
accueil();
if ('serviceWorker' in navigator && location.protocol.startsWith('http')) navigator.serviceWorker.register('sw.js').catch(() => {});
