// echoSystem Dashboard Logic v1.1.1
// Refined UX Bricks Integration

let currentPath = [];
let socket = null;
let currentCategory = 'all';

// Component mapping
const elements = {
    grid: document.getElementById('file-grid'),
    empty: document.getElementById('empty-state'),
    breadcrumbs: document.getElementById('breadcrumb-container'),
    uploadBtn: document.getElementById('upload-btn'),
    fileInput: document.getElementById('file-input'),
    previewModal: document.getElementById('preview-modal'),
    modalContent: document.getElementById('preview-content'),
    modalTitle: document.getElementById('preview-title'),
    modalSize: document.getElementById('preview-size'),
    modalType: document.getElementById('preview-type'),
    modalDownload: document.getElementById('preview-download'),
    closePreview: document.getElementById('close-preview'),
    modalOverlay: document.getElementById('modal-overlay'),
    toast: document.getElementById('toast'),
    queueList: document.getElementById('queue-list'),
    queueContainer: document.getElementById('transfer-queue'),
    queueCount: document.getElementById('queue-count')
};

// --- CORE LOGIC ---

async function fetchFiles(path = []) {
    const pathStr = path.join('/');
    try {
        const response = await fetch(`/api/files-tree?path=${encodeURIComponent(pathStr)}`);
        const files = await response.json();
        renderGrid(files);
        updateBreadcrumbs();
    } catch (err) {
        showToast('Error', 'Registry Sync Failed', true);
    }
}

function renderGrid(files) {
    elements.grid.innerHTML = '';
    
    const filtered = files.filter(f => {
        if (currentCategory === 'all') return true;
        const ext = f.name.split('.').pop().toLowerCase();
        if (currentCategory === 'photos') return ['jpg', 'jpeg', 'png', 'webp', 'gif'].includes(ext);
        if (currentCategory === 'docs') return ['pdf', 'txt', 'doc', 'docx', 'xls', 'xlsx'].includes(ext);
        if (currentCategory === 'media') return ['mp4', 'mkv', 'mov', 'mp3', 'wav', 'm4a'].includes(ext);
        return false;
    });

    if (filtered.length === 0) {
        elements.grid.classList.add('hidden');
        elements.empty.classList.remove('hidden');
        return;
    }

    elements.grid.classList.remove('hidden');
    elements.empty.classList.add('hidden');

    filtered.forEach(file => {
        const card = document.createElement('div');
        card.className = "group relative glass p-4 rounded-2xl cursor-pointer hover:bg-white/5 transition-all card-shadow fade-in";
        card.innerHTML = `
            <div class="aspect-square bg-white/5 rounded-xl mb-4 flex items-center justify-center overflow-hidden">
                ${getFileIcon(file)}
            </div>
            <div class="pr-6">
                <h3 class="font-medium text-sm truncate mb-1" title="${file.name}">${file.name}</h3>
                <p class="text-[10px] mono opacity-40 uppercase tracking-widest">${file.isDirectory ? 'Volume' : formatBytes(file.size)}</p>
            </div>
            <div class="absolute top-4 right-4 opacity-0 group-hover:opacity-100 transition-opacity bg-resonance-accent/20 p-2 rounded-lg">
                <i data-lucide="${file.isDirectory ? 'corner-down-right' : 'eye'}" class="w-3 h-3 text-resonance-accent"></i>
            </div>
        `;

        card.onclick = () => {
            if (file.isDirectory) {
                currentPath.push(file.name);
                fetchFiles(currentPath);
            } else {
                openPreview(file);
            }
        };

        elements.grid.appendChild(card);
    });
    
    lucide.createIcons();
}

function getFileIcon(file) {
    const ext = file.name.split('.').pop().toLowerCase();
    
    if (file.isDirectory) return `<i data-lucide="folder" class="w-10 h-10 text-resonance-accent/40"></i>`;
    
    if (['jpg', 'jpeg', 'png', 'webp', 'gif'].includes(ext)) {
        return `<img src="/api/files/download?path=${encodeURIComponent([...currentPath, file.name].join('/'))}" class="w-full h-full object-contain" loading="lazy">`;
    }
    
    if (['mp4', 'webm', 'mov'].includes(ext)) return `<i data-lucide="play-circle" class="w-10 h-10 text-blue-400"></i>`;
    if (['mp3', 'wav', 'm4a', 'flac'].includes(ext)) return `<i data-lucide="music" class="w-10 h-10 text-purple-400"></i>`;
    if (['pdf', 'txt', 'md', 'json'].includes(ext)) return `<i data-lucide="file-text" class="w-10 h-10 text-orange-400"></i>`;
    
    return `<i data-lucide="file" class="w-10 h-10 text-white/20"></i>`;
}

// --- PREVIEW LOGIC (BRICK 1) ---

function openPreview(file) {
    const path = [...currentPath, file.name].join('/');
    const url = `/api/files/download?path=${encodeURIComponent(path)}`;
    const ext = file.name.split('.').pop().toLowerCase();

    elements.modalTitle.innerText = file.name;
    elements.modalSize.innerText = formatBytes(file.size);
    elements.modalType.innerText = ext;
    elements.modalDownload.onclick = () => window.open(url, '_blank');
    
    elements.modalContent.innerHTML = '';
    
    if (['jpg', 'jpeg', 'png', 'webp', 'gif'].includes(ext)) {
        elements.modalContent.innerHTML = `<img src="${url}" class="max-w-full max-h-full object-contain">`;
    } else if (['mp4', 'webm', 'mov'].includes(ext)) {
        elements.modalContent.innerHTML = `<video controls class="max-w-full max-h-full" autoplay><source src="${url}"></video>`;
    } else if (['mp3', 'wav', 'm4a'].includes(ext)) {
        elements.modalContent.innerHTML = `
            <div class="flex flex-col items-center">
                <i data-lucide="music" class="w-32 h-32 text-resonance-accent mb-8 opacity-20"></i>
                <audio controls src="${url}" class="w-72"></audio>
            </div>
        `;
    } else if (['pdf', 'txt', 'md', 'json', 'js', 'html', 'css'].includes(ext)) {
        elements.modalContent.innerHTML = `<iframe src="${url}" class="w-full h-full bg-white rounded-lg"></iframe>`;
    } else {
        elements.modalContent.innerHTML = `
            <div class="text-center">
                <i data-lucide="file" class="w-32 h-32 text-white/5 mb-6"></i>
                <p class="opacity-50">Preview not supported for this resonance type.</p>
            </div>
        `;
    }

    elements.previewModal.classList.remove('hidden');
    lucide.createIcons();
}

function closePreview() {
    elements.previewModal.classList.add('hidden');
    elements.modalContent.innerHTML = '';
}

// --- TRANSFERS & WEBSOCKET ---

function initWebSocket() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws`;
    socket = new WebSocket(wsUrl);
    
    socket.onopen = () => {
        console.log('Mesh Socket Connected');
    };

    socket.onmessage = (event) => {
        const data = JSON.parse(event.data);
        if (data.type === 'transfer_progress') handleTransferProgress(data);
        if (data.type === 'transfer_complete') handleTransferComplete(data);
        if (data.type === 'file_change') fetchFiles(currentPath);
    };

    socket.onclose = () => {
        setTimeout(initWebSocket, 5000);
    };
}

const activeTransfers = new Map();

function handleTransferProgress(data) {
    activeTransfers.set(data.id, data);
    updateQueueUI();
}

function handleTransferComplete(data) {
    activeTransfers.delete(data.id);
    updateQueueUI();
    showToast('Registry Synced', data.fileName);
    document.body.classList.add('flash-green');
    setTimeout(() => document.body.classList.remove('flash-green'), 1000);
}

function updateQueueUI() {
    if (activeTransfers.size > 0) {
        elements.queueContainer.classList.remove('hidden');
        elements.queueCount.innerText = activeTransfers.size;
    } else {
        elements.queueContainer.classList.add('hidden');
    }

    elements.queueList.innerHTML = '';
    activeTransfers.forEach(t => {
        const item = document.createElement('div');
        item.className = "text-xs space-y-2";
        item.innerHTML = `
            <div class="flex justify-between font-mono">
                <span class="truncate pr-4 opacity-70">${t.fileName}</span>
                <span>${Math.round(t.progress * 100)}%</span>
            </div>
            <div class="w-full h-1 bg-white/10 rounded-full overflow-hidden">
                <div class="h-full bg-resonance-accent transition-all duration-300" style="width: ${t.progress * 100}%"></div>
            </div>
        `;
        elements.queueList.appendChild(item);
    });
}

// --- UTILS ---

function updateBreadcrumbs() {
    elements.breadcrumbs.innerHTML = `
        <button class="hover:text-resonance-accent transition-colors" onclick="navigateHome()">
            <i data-lucide="home" class="w-4 h-4"></i>
        </button>
    `;
    
    currentPath.forEach((dir, i) => {
        elements.breadcrumbs.innerHTML += `
            <span class="opacity-30">/</span>
            <button class="hover:text-resonance-accent transition-colors" onclick="navigateToIndex(${i})">${dir}</button>
        `;
    });
    lucide.createIcons();
}

function navigateHome() {
    currentPath = [];
    fetchFiles();
}

function navigateToIndex(index) {
    currentPath = currentPath.slice(0, index + 1);
    fetchFiles(currentPath);
}

function showToast(msg, sub = '', isError = false) {
    const toast = elements.toast;
    const msgEl = document.getElementById('toast-msg');
    const subEl = document.getElementById('toast-sub');
    
    msgEl.innerText = msg;
    subEl.innerText = sub;
    
    toast.classList.remove('translate-y-20', 'opacity-0');
    if (isError) toast.classList.add('shake');
    
    setTimeout(() => {
        toast.classList.add('translate-y-20', 'opacity-0');
        toast.classList.remove('shake');
    }, 4000);
}

function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

// --- EVENT LISTENERS ---

elements.uploadBtn.onclick = () => elements.fileInput.click();

elements.fileInput.onchange = async () => {
    const files = elements.fileInput.files;
    if (files.length === 0) return;

    for (const file of files) {
        const formData = new FormData();
        formData.append('file', file);
        const pathPrefix = currentPath.length > 0 ? currentPath.join('/') + '/' : '';
        
        try {
            await fetch(`/api/files/upload?path=${encodeURIComponent(pathPrefix + file.name)}`, {
                method: 'POST',
                body: formData
            });
            showToast('Transmission Initialized', file.name);
        } catch (err) {
            showToast('Failed to Transmit', file.name, true);
        }
    }
    elements.fileInput.value = '';
};

elements.closePreview.onclick = closePreview;
elements.modalOverlay.onclick = closePreview;

document.querySelectorAll('.category-tab').forEach(tab => {
    tab.onclick = () => {
        document.querySelectorAll('.category-tab').forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        currentCategory = tab.dataset.category;
        fetchFiles(currentPath);
    };
});

initWebSocket();
fetchFiles();
