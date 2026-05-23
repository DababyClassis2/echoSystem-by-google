let pairingPin = localStorage.getItem('pairingPin') || '';
let deviceId = localStorage.getItem('deviceId') || '';
let currentPath = ''; // Relative to echoSystem/
let fileLibrary = [];
let searchQuery = '';
let userPermissions = [];
let isPaired = false;

if (!deviceId) {
    deviceId = 'web-' + Math.random().toString(36).substring(2, 10);
    localStorage.setItem('deviceId', deviceId);
}

const authOverlay = document.getElementById('authSection');
const managementSection = document.getElementById('managementSection');
const contentGrid = document.getElementById('contentGrid');
const queueList = document.getElementById('queueList');
const queueSection = document.getElementById('queueSection');
const folderTree = document.getElementById('folderTree');
const breadcrumb = document.getElementById('breadcrumb');
const manageBtn = document.getElementById('manageBtn');

// Icons Mapping
const ICONS = {
    folder: 'folder-closed',
    image: 'image',
    video: 'play-square',
    music: 'music',
    document: 'file-text',
    other: 'file'
};

const COLORS = {
    folder: 'text-amber-400 bg-amber-400/10',
    image: 'text-emerald-400 bg-emerald-400/10',
    video: 'text-purple-400 bg-purple-400/10',
    music: 'text-rose-400 bg-rose-400/10',
    document: 'text-blue-400 bg-blue-400/10',
    other: 'text-slate-400 bg-white/5'
};

async function initialize() {
    await checkAutoAuth();
    setupWebSocket();
    lucide.createIcons();
}

async function checkAutoAuth() {
    if (!pairingPin) {
        showAuth();
        return;
    }
    await validateNode();
}

async function validateNode() {
    try {
        const res = await fetch('/web/permissions', {
            headers: { 'X-PIN': pairingPin, 'X-Device-Id': deviceId }
        });
        if (res.ok) {
            userPermissions = await res.json();
            isPaired = true;
            hideAuth();
            updateUIPermissions();
            loadContent();
            updateStatus(true);
        } else {
            showAuth();
            if (res.status === 403) authError.textContent = "Identity Revoked by Host.";
        }
    } catch (e) {
        updateStatus(false);
    }
}

function updateStatus(online) {
    const dot = document.getElementById('statusDot');
    const text = document.getElementById('statusText');
    if (online) {
        dot.className = "w-2 h-2 rounded-full bg-emerald-500 shadow-[0_0_8px_#10b981]";
        text.textContent = "Node Synchronized";
        text.className = "text-[10px] font-black uppercase tracking-widest text-emerald-500";
    } else {
        dot.className = "w-2 h-2 rounded-full bg-red-500";
        text.textContent = "Node Disconnected";
        text.className = "text-[10px] font-black uppercase tracking-widest text-red-500";
    }
}

function showAuth() {
    authOverlay.classList.remove('opacity-0', 'pointer-events-none');
    authOverlay.classList.add('opacity-100');
}

function hideAuth() {
    authOverlay.classList.add('opacity-0', 'pointer-events-none');
    authOverlay.classList.remove('opacity-100');
}

document.getElementById('authForm').onsubmit = async (e) => {
    e.preventDefault();
    const pin = document.getElementById('pinInput').value;
    const err = document.getElementById('authError');
    err.classList.add('hidden');

    try {
        const res = await fetch('/pairing/request', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ deviceId, pin, deviceName: 'Web Dashboard v2' })
        });

        if (res.ok || res.status === 401) {
            pairingPin = pin;
            localStorage.setItem('pairingPin', pin);
            validateNode();
        } else {
            err.textContent = "Invalid PIN Sequence";
            err.classList.remove('hidden');
        }
    } catch (err) {
        console.error(err);
    }
};

function updateUIPermissions() {
    if (userPermissions.includes('MANAGE_PERMISSIONS')) {
        manageBtn.classList.remove('hidden');
    }
    const canUpload = userPermissions.includes('UPLOAD_FILES') || userPermissions.includes('MANAGE_PERMISSIONS');
    const uploadFab = document.getElementById('uploadFab');
    const uploadZone = document.getElementById('uploadZone');
    
    if (uploadFab) uploadFab.style.display = canUpload ? 'flex' : 'none';
    if (uploadZone) uploadZone.style.display = canUpload ? 'block' : 'none';
}

async function loadContent() {
    try {
        contentGrid.classList.add('opacity-0');
        const res = await fetch(`/web/files?path=${encodeURIComponent(currentPath)}`, {
            headers: { 'X-PIN': pairingPin, 'X-Device-Id': deviceId }
        });
        if (res.ok) {
            const data = await res.json();
            fileLibrary = data.items || [];
            setTimeout(() => {
                renderContent();
                renderFolderTree();
                updateBreadcrumb();
            }, 200);
        } else {
            contentGrid.classList.remove('opacity-0');
        }
    } catch (e) { 
        contentGrid.classList.remove('opacity-0');
        console.error(e); 
    }
}

function renderContent() {
    contentGrid.innerHTML = '';
    let filtered = fileLibrary;
    if (searchQuery) {
        filtered = filtered.filter(f => f.name.toLowerCase().includes(searchQuery.toLowerCase()));
    }

    document.getElementById('itemCount').textContent = `${filtered.length} Items`;
    document.getElementById('currentPathDisplay').textContent = currentPath ? `/${currentPath}` : '/root';

    if (filtered.length === 0) {
        contentGrid.innerHTML = `
            <div class="col-span-full py-32 text-center opacity-30 flex flex-col items-center gap-4">
                <i data-lucide="ghost" class="w-16 h-16"></i>
                <p class="text-xs font-black uppercase tracking-[0.3em]">No fragments detected</p>
            </div>
        `;
        lucide.createIcons();
        return;
    }

    filtered.forEach((item, idx) => {
        const card = document.createElement('div');
        card.className = "file-card glass rounded-3xl p-5 flex flex-col gap-4 animate-slide cursor-pointer";
        card.style.animationDelay = `${idx * 20}ms`;
        card.onclick = (e) => {
            // Only trigger if we didn't click a button specifically
            if(!e.target.closest('button')) {
                if(item.isDirectory) navigate(item.name);
                else openPreview(item);
            }
        };

        const icon = ICONS[item.type] || 'file';
        const colorClass = COLORS[item.type] || COLORS.other;

        const canDelete = userPermissions.includes('DELETE_FILES') || userPermissions.includes('MANAGE_PERMISSIONS');
        const canDownload = !item.isDirectory && (userPermissions.includes('DOWNLOAD_FILES') || userPermissions.includes('MANAGE_PERMISSIONS'));

        card.innerHTML = `
            <div class="flex items-start justify-between">
                <div class="w-14 h-14 rounded-2xl flex items-center justify-center ${colorClass}">
                    <i data-lucide="${icon}" class="w-7 h-7"></i>
                </div>
                <div class="flex gap-1">
                    ${canDownload ? `
                         <button onclick="event.stopPropagation(); downloadFile('${item.name}')" class="p-2 text-slate-500 hover:text-indigo-400 transition-colors">
                            <i data-lucide="download" class="w-4 h-4"></i>
                        </button>
                    ` : ''}
                    ${canDelete ? `
                        <button onclick="event.stopPropagation(); deleteObject('${item.name}')" class="p-2 text-slate-500 hover:text-red-400 transition-colors">
                            <i data-lucide="trash-2" class="w-4 h-4"></i>
                        </button>
                    ` : ''}
                </div>
            </div>
            <div class="min-w-0">
                <h4 class="font-bold text-sm truncate leading-tight mb-1">${item.name}</h4>
                <p class="text-[10px] font-black text-slate-500 uppercase tracking-widest">${item.isDirectory ? 'Directory' : `${item.formattedSize} • ${item.extension.toUpperCase()}`}</p>
            </div>
        `;
        contentGrid.appendChild(card);
    });
    lucide.createIcons();
    requestAnimationFrame(() => {
        contentGrid.classList.remove('opacity-0');
    });
}

function renderFolderTree() {
    const defaultFolders = ['Received', 'Sent', 'Shared'];
    folderTree.innerHTML = `
        <div onclick="resetPath()" class="tree-item flex items-center gap-3 p-3 rounded-xl cursor-pointer ${!currentPath ? 'active' : 'text-slate-400'}">
            <i data-lucide="layout-grid" class="w-4 h-4"></i>
            <span class="text-xs font-bold tracking-tight">Root Repository</span>
        </div>
    `;
    
    defaultFolders.forEach(folder => {
        const isActive = currentPath.startsWith(folder);
        folderTree.innerHTML += `
            <div onclick="navigate('${folder}')" class="tree-item flex items-center gap-3 p-3 rounded-xl cursor-pointer ${isActive ? 'active' : 'text-slate-400'}">
                <i data-lucide="folder" class="w-4 h-4"></i>
                <span class="text-xs font-bold tracking-tight">${folder}</span>
            </div>
        `;
    });
    lucide.createIcons();
}

function updateBreadcrumb() {
    breadcrumb.innerHTML = `<span onclick="resetPath()" class="cursor-pointer hover:text-indigo-400 transition-colors">echoSystem</span>`;
    if (currentPath) {
        const parts = currentPath.split('/');
        let builtPath = '';
        parts.forEach(p => {
            builtPath += (builtPath ? '/' : '') + p;
            const currentClosure = builtPath;
            breadcrumb.innerHTML += `
                <i data-lucide="chevron-right" class="w-3 h-3 mx-1 opacity-30"></i>
                <span onclick="setPath('${currentClosure}')" class="cursor-pointer hover:text-indigo-400 transition-colors">${p}</span>
            `;
        });
    }
    lucide.createIcons();
}

function navigate(dir) {
    if (currentPath) currentPath += '/' + dir;
    else currentPath = dir;
    loadContent();
}

// Set explicit navigation path
function setPath(path) {
    currentPath = path;
    loadContent();
}

// Clear folder navigation pointers
function resetPath() {
    currentPath = '';
    loadContent();
}

function downloadFile(name) {
    const url = getFileUrl(name);
    window.location.href = url;
}

function getFileUrl(name) {
    return `/web/download?fileName=${encodeURIComponent(name)}&path=${encodeURIComponent(currentPath)}&pin=${pairingPin}&deviceId=${deviceId}`;
}

async function openPreview(item) {
    const modal = document.getElementById('previewModal');
    const title = document.getElementById('previewTitle');
    const meta = document.getElementById('previewMeta');
    const content = document.getElementById('previewContent');
    const icon = document.getElementById('previewIcon');
    const dlBtn = document.getElementById('previewDownloadBtn');

    title.textContent = item.name;
    meta.textContent = `${item.formattedSize} • ${item.extension.toUpperCase()}`;
    icon.className = `w-10 h-10 rounded-xl flex items-center justify-center ${COLORS[item.type] || COLORS.other}`;
    icon.innerHTML = `<i data-lucide="${ICONS[item.type] || 'file'}" class="w-5 h-5"></i>`;
    dlBtn.onclick = () => downloadFile(item.name);

    content.innerHTML = '<i data-lucide="loader-2" class="w-12 h-12 animate-spin opacity-20"></i>';
    lucide.createIcons();

    modal.classList.remove('opacity-0', 'pointer-events-none');
    
    const fileUrl = getFileUrl(item.name);

    if (item.type === 'image') {
        content.innerHTML = `<img src="${fileUrl}" class="max-w-full max-h-full object-contain shadow-2xl rounded-lg animate-in fade-in duration-500">`;
    } else if (item.type === 'video') {
        content.innerHTML = `<video src="${fileUrl}" controls autoplay muted class="max-w-full max-h-full rounded-lg shadow-2xl"></video>`;
    } else if (item.type === 'music') {
        content.innerHTML = `
            <div class="text-center">
                <i data-lucide="music" class="w-32 h-32 text-indigo-500/20 mb-8 mx-auto"></i>
                <audio src="${fileUrl}" controls autoplay class="w-80 md:w-96"></audio>
            </div>
        `;
    } else if (item.type === 'document' && ['txt', 'log', 'json', 'xml', 'md', 'js', 'css', 'html'].includes(item.extension)) {
        try {
            const res = await fetch(fileUrl, { headers: { 'X-PIN': pairingPin, 'X-Device-Id': deviceId } });
            const text = await res.text();
            content.innerHTML = `
                <div class="w-full h-full glass border border-white/5 rounded-3xl p-8 overflow-auto sidebar-scroller">
                    <pre class="text-xs font-mono text-slate-300 leading-relaxed">${text.substring(0, 50000).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')}</pre>
                </div>
            `;
        } catch (e) {
            content.innerHTML = `<p class="text-xs font-bold uppercase tracking-widest text-red-400">Failed to stream resource data</p>`;
        }
    } else {
        content.innerHTML = `
            <div class="text-center opacity-40">
                <i data-lucide="eye-off" class="w-20 h-20 mb-4 mx-auto text-slate-600"></i>
                <p class="text-xs font-black uppercase tracking-[0.3em]">No Visual Stream Available</p>
                <p class="text-[10px] font-bold mt-2 lowercase text-slate-500">${item.extension.toUpperCase()} Format</p>
            </div>
        `;
    }
    lucide.createIcons();
}

function closePreview() {
    const modal = document.getElementById('previewModal');
    const content = document.getElementById('previewContent');
    modal.classList.add('opacity-0', 'pointer-events-none');
    setTimeout(() => {
        content.innerHTML = ''; // Stop any playing media
    }, 300);
}

async function deleteObject(name) {
    if (!confirm(`Permanently wipe "${name}" from remote disk?`)) return;
    try {
        const res = await fetch(`/web/delete?fileName=${encodeURIComponent(name)}&path=${encodeURIComponent(currentPath)}`, {
            method: 'POST',
            headers: { 'X-PIN': pairingPin, 'X-Device-Id': deviceId }
        });
        if (res.ok) loadContent();
    } catch (e) { console.error(e); }
}

// Management Logic
function openManagement() {
    managementSection.classList.remove('opacity-0', 'pointer-events-none');
    managementSection.classList.add('opacity-100');
    loadRegistry();
}

function closeManagement() {
    managementSection.classList.add('opacity-0', 'pointer-events-none');
    managementSection.classList.remove('opacity-100');
}

async function loadRegistry() {
    try {
        const res = await fetch('/management/trusted', {
            headers: { 'X-PIN': pairingPin, 'X-Device-Id': deviceId }
        });
        if (res.ok) {
            const devices = await res.json();
            const deviceList = document.getElementById('deviceList');
            deviceList.innerHTML = '';
            
            devices.forEach(dev => {
                const card = document.createElement('div');
                card.className = `glass p-8 rounded-[2rem] border ${dev.blocked ? 'border-red-500/20 bg-red-500/5' : 'border-white/5'}`;
                
                const perms = ['BROWSE_FILES', 'UPLOAD_FILES', 'DOWNLOAD_FILES', 'DELETE_FILES', 'MANAGE_PERMISSIONS'];
                let permHtml = perms.map(p => {
                    const active = dev.permissions.includes(p);
                    return `
                        <button onclick="toggleDevicePerm('${dev.id}', '${p}', ${active})" 
                            class="px-3 py-1.5 rounded-lg text-[9px] font-black uppercase tracking-widest transition-all ${active ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-600/30' : 'bg-white/5 text-slate-500'}">
                            ${p.replace('_FILES', '').replace('MANAGE_', 'ADMIN ')}
                        </button>
                    `;
                }).join('');

                card.innerHTML = `
                    <div class="flex items-start justify-between mb-6">
                        <div class="flex items-center gap-4">
                            <div class="w-12 h-12 bg-white/5 rounded-2xl flex items-center justify-center text-slate-500">
                                <i data-lucide="smartphone" class="w-6 h-6"></i>
                            </div>
                            <div>
                                <h4 class="font-bold text-lg leading-tight flex items-center gap-2">
                                    ${dev.name}
                                    <button onclick="renameNode('${dev.id}', '${dev.name}')" class="p-1 text-slate-700 hover:text-indigo-400"><i data-lucide="edit-3" class="w-3 h-3"></i></button>
                                </h4>
                                <p class="text-[10px] font-mono text-slate-600 truncate max-w-[150px]">${dev.id}</p>
                            </div>
                        </div>
                        <button onclick="toggleNodeState('${dev.id}', ${dev.blocked})" class="px-4 py-2 rounded-xl text-[10px] font-black uppercase tracking-widest transition-all ${dev.blocked ? 'bg-red-500 text-white' : 'bg-white/5 text-slate-400 hover:text-red-400'}">
                            ${dev.blocked ? 'Unblock' : 'Block'}
                        </button>
                    </div>
                    <div class="flex flex-wrap gap-2">
                        ${permHtml}
                    </div>
                `;
                deviceList.appendChild(card);
            });
            lucide.createIcons();
        }
    } catch (e) { console.error(e); }
}

async function renameNode(targetId, oldName) {
    const newName = prompt("New alias for node:", oldName);
    if (!newName || newName === oldName) return;
    const res = await fetch('/management/rename', {
        method: 'POST',
        headers: { 'X-PIN': pairingPin, 'X-Device-Id': deviceId, 'Content-Type': 'application/json' },
        body: JSON.stringify({ targetDeviceId: targetId, newName })
    });
    if (res.ok) loadRegistry();
}

async function toggleNodeState(targetId, currentBlocked) {
    const res = await fetch('/management/block', {
        method: 'POST',
        headers: { 'X-PIN': pairingPin, 'X-Device-Id': deviceId, 'Content-Type': 'application/json' },
        body: JSON.stringify({ targetDeviceId: targetId, blocked: !currentBlocked })
    });
    if (res.ok) loadRegistry();
}

async function toggleDevicePerm(targetId, perm, isActive) {
    // We need current permissions to modify them
    const res = await fetch('/management/trusted', { headers: { 'X-PIN': pairingPin, 'X-Device-Id': deviceId } });
    const devices = await res.json();
    const dev = devices.find(d => d.id === targetId);
    if (!dev) return;

    let newPermissions = dev.permissions;
    if (isActive) newPermissions = newPermissions.filter(p => p !== perm);
    else newPermissions = [...newPermissions, perm];

    const upRes = await fetch('/management/permissions', {
        method: 'POST',
        headers: { 'X-PIN': pairingPin, 'X-Device-Id': deviceId, 'Content-Type': 'application/json' },
        body: JSON.stringify({ targetDeviceId: targetId, permissions: newPermissions })
    });
    if (upRes.ok) loadRegistry();
}

// Upload Handling
document.getElementById('uploadZone').onclick = () => document.getElementById('fileSelector').click();
document.getElementById('fileSelector').onchange = (e) => {
    const files = Array.from(e.target.files);
    files.forEach(syncFile);
};

// Queue handling elements safely
function syncFile(file) {
    queueSection.classList.remove('queue-hidden');
    const id = 'sync-' + Math.random().toString(36).substring(2, 7);
    const row = document.createElement('div');
    row.id = id;
    row.className = "bg-white/5 border border-white/5 rounded-2xl p-5 flex items-center justify-between gap-4";
    row.innerHTML = `
        <div class="flex items-center gap-4 min-w-0 flex-1">
            <div class="w-10 h-10 bg-indigo-500/10 text-indigo-400 rounded-xl flex items-center justify-center shrink-0">
                <i data-lucide="upload" class="w-5 h-5"></i>
            </div>
            <div class="min-w-0 flex-1">
                <p class="text-xs font-bold truncate text-slate-200">${file.name}</p>
                <div class="h-1 bg-white/5 rounded-full mt-2 w-full overflow-hidden">
                    <div id="bar-${id}" class="h-full bg-indigo-500 w-0 transition-all duration-300"></div>
                </div>
            </div>
        </div>
        <div class="text-right">
            <p id="pct-${id}" class="text-[10px] font-black text-indigo-400">0%</p>
            <p class="text-[8px] font-bold text-slate-600 uppercase tracking-widest mt-0.5">Pushing</p>
        </div>
    `;
    queueList.appendChild(row);
    lucide.createIcons();
    updateQueueCount();

    const xhr = new XMLHttpRequest();
    const fd = new FormData();
    fd.append('file', file);
    xhr.open('POST', `/web/upload?path=${encodeURIComponent(currentPath)}`);
    xhr.setRequestHeader('X-PIN', pairingPin);
    xhr.setRequestHeader('X-Device-Id', deviceId);
    
    xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) {
            const p = Math.round((e.loaded / e.total) * 100);
            document.getElementById(`bar-${id}`).style.width = p + '%';
            document.getElementById(`pct-${id}`).textContent = p + '%';
        }
    };
    
    xhr.onload = () => {
        if (xhr.status === 200) {
            document.getElementById(id).classList.add('border-emerald-500/30', 'bg-emerald-500/5');
            setTimeout(() => {
                document.getElementById(id).remove();
                updateQueueCount();
                if (!queueList.children.length) queueSection.classList.add('queue-hidden');
            }, 3000);
            loadContent();
        }
    };
    xhr.send(fd);
}

function updateQueueCount() {
    document.getElementById('queueCount').textContent = queueList.children.length;
}

document.getElementById('searchInput').oninput = (e) => {
    searchQuery = e.target.value;
    renderContent();
};

async function createFolder() {
    const name = prompt("Enter folder name:");
    if (!name) return;
    const folderPath = currentPath ? `${currentPath}/${name}` : name;
    try {
        const res = await fetch('/web/mkdir', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-PIN': pairingPin,
                'X-Device-Id': deviceId
            },
            body: JSON.stringify({ path: folderPath })
        });
        if (res.ok) {
            loadContent();
        } else {
            alert("Failed to create folder. Make sure you have upload permission.");
        }
    } catch (e) {
        console.error(e);
        alert("Network Error");
    }
}

let ws = null;
function setupWebSocket() {
    if (ws) {
        try { ws.close(); } catch(e){}
        ws = null;
    }
    const loc = window.location;
    const wsProto = loc.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${wsProto}//${loc.host}/events`;
    ws = new WebSocket(wsUrl);

    ws.onmessage = (event) => {
        try {
            const msg = JSON.parse(event.data);
            if (msg.type === 'file_changed' || msg.type === 'transfer_completed' || msg.type === 'transfer_started') {
                loadContent();
            } else if (msg.type === 'device_online' || msg.type === 'device_offline') {
                if (!managementSection.classList.contains('pointer-events-none')) {
                    loadRegistry();
                }
            }
        } catch (e) {
            console.error("WS error: ", e);
        }
    };

    ws.onclose = () => {
        setTimeout(setupWebSocket, 5000);
    };
}

window.onload = initialize;
