let currentPath = [];
let socket = null;

async function fetchFiles() {
    const res = await fetch(`/api/files-tree?path=${encodeURIComponent(currentPath.join('/'))}`);
    const files = await res.json();
    const grid = document.getElementById('file-grid');
    grid.innerHTML = '';
    files.forEach(f => {
        const div = document.createElement('div');
        div.className = "glass p-4 rounded-2xl cursor-pointer hover:bg-white/5";
        div.innerHTML = `<h3 class="truncate text-sm font-medium">${f.name}</h3>`;
        div.onclick = () => { if(f.isDirectory) { currentPath.push(f.name); fetchFiles(); } else { openPreview(f); } };
        grid.appendChild(div);
    });
}

function openPreview(file) {
    const modal = document.getElementById('preview-modal');
    const content = document.getElementById('preview-content');
    modal.classList.remove('hidden');
    content.innerHTML = `<h2 class="text-xl font-bold">${file.name}</h2>`;
}

document.getElementById('close-preview').onclick = () => document.getElementById('preview-modal').classList.add('hidden');
fetchFiles();
