'use strict';
// Content is constructed as text nodes. Payloads never become HTML, CSS or JavaScript.
function inline(parent, text) {
    const pattern = /\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)|\*\*(.+?)\*\*|__(.+?)__|\*(.+?)\*|_(.+?)_|`([^`]+)`/g;
    let end = 0;
    for (const match of text.matchAll(pattern)) {
        parent.append(document.createTextNode(text.slice(end, match.index)));
        const tag = match[1] !== undefined ? 'a' : match[3] !== undefined || match[4] !== undefined ? 'strong' : match[5] !== undefined || match[6] !== undefined ? 'em' : 'code';
        const node = document.createElement(tag);
        if (tag === 'a') { node.href = match[2]; node.target = '_blank'; node.rel = 'noopener noreferrer'; }
        node.textContent = match[1] ?? match[3] ?? match[4] ?? match[5] ?? match[6] ?? match[7];
        parent.append(node); end = match.index + match[0].length;
    }
    parent.append(document.createTextNode(text.slice(end)));
}
function content(parent, text) {
    parent.replaceChildren(); let paragraph = [];
    function flush() { if (paragraph.length) { const item = document.createElement('p'); inline(item, paragraph.join('\n')); parent.append(item); paragraph = []; } }
    for (const line of String(text || '').replace(/\r\n?/g, '\n').split('\n')) {
        const value = line.trim(); if (!value) { flush(); continue; }
        const heading = /^(#{1,6})\s+(.+)$/.exec(value);
        const bullet = /^(?:[-*+•]|(\d+)[.)])\s+(.+)$/.exec(value);
        const callout = /^>\s?(.*)$/.exec(value);
        if (heading || callout) {
            flush(); const item = document.createElement(heading ? 'h2' : 'blockquote');
            if (heading) item.dataset.level = String(heading[1].length);
            inline(item, heading?.[2] ?? callout[1]); parent.append(item);
        } else if (bullet) {
            flush(); const item = document.createElement('div'); item.className = 'list-row';
            const marker = document.createElement('span'); marker.textContent = bullet[1] ? bullet[1] + '.' : '•';
            const copy = document.createElement('p'); inline(copy, bullet[2]); item.append(marker, copy); parent.append(item);
        } else paragraph.push(line);
    }
    flush();
}
function notify(action) {
    window.parent.postMessage({source:'boxlore-announcement', action}, location.origin);
}
window.renderAnnouncement = function (payload, profile = {}) {
    const colors = profile.colors || {};
    for (const [key, value] of Object.entries(colors)) {
        if (/^(surface|container|text|muted|accent|on-accent|accent-container|on-container|tonal-container|on-tonal-container)$/.test(key) && /^#[0-9a-f]{6}$/i.test(value)) document.documentElement.style.setProperty('--' + key, value);
    }
    document.documentElement.style.setProperty('--scale', String(Math.min(2, Math.max(.85, Number(profile.fontScale) || 1))));
    document.documentElement.style.setProperty('--roundness', String(Math.min(100, Math.max(0, Number(profile.roundness ?? 100)))));
    document.documentElement.dir = profile.rtl ? 'rtl' : 'ltr';
    document.body.classList.toggle('fullscreen', payload.presentation === 'fullscreen');
    document.getElementById('badge').textContent = payload.category || 'ANNOUNCEMENT';
    document.getElementById('title').textContent = payload.title || '';
    const body = document.getElementById('body');
    if (body.dataset.copy !== String(payload.body || '')) { content(body, payload.body); body.dataset.copy = String(payload.body || ''); }
    const image = document.getElementById('image');
    let url; try { url = new URL(payload.image); } catch (_) { /* no image */ }
    image.className = payload.image_style === 'cover' ? 'cover' : '';
    image.style.display = url?.protocol === 'https:' && !url.username && !url.password ? 'block' : 'none';
    image.onerror = () => { image.style.display = 'none'; };
    if (image.style.display === 'block') image.src = url.href; else image.removeAttribute('src');
    const action = document.getElementById('action');
    action.textContent = payload.action_label || 'Open';
    const release = payload.release_alert === 'true' || (payload.release_alert == null && /^(WHAT[’']?S NEW|NEW RELEASE)$/i.test(payload.category || ''));
    document.querySelector('footer').classList.toggle('release', release);
    action.hidden = release || payload.show_action_in_app === 'false' || !payload.route;
    const download = document.getElementById('download');
    download.hidden = !release; download.textContent = profile.play ? (profile.playLabel || 'Update on Google Play') : (profile.downloadLabel || 'Download update');
    download.onclick = () => notify('download');
    const github = document.getElementById('github');
    github.hidden = !release; github.textContent = profile.githubLabel || 'View on GitHub';
    github.onclick = () => notify('github');
    action.onclick = () => notify('action');
    document.getElementById('close').onclick = document.getElementById('dismiss').onclick = () => notify('dismiss');
    document.getElementById('close').ariaLabel = profile.closeLabel || 'Close announcement';
    document.getElementById('dismiss').textContent = profile.dismissLabel || 'Dismiss';
    document.getElementById('alert').style.visibility = 'visible';
};
window.addEventListener('message', event => { if (event.origin === location.origin && event.source === window.parent && event.data?.source === 'boxlore-preview') window.renderAnnouncement(event.data.payload, event.data.profile); });
window.parent.postMessage({source:'boxlore-announcement', action:'ready'}, location.origin);
