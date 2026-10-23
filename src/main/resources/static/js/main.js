document.addEventListener('DOMContentLoaded', () => {
    // 1. Auto-dismiss flash alerts after 5 seconds
    const alerts = document.querySelectorAll('.alert');
    alerts.forEach(alert => {
        setTimeout(() => {
            alert.style.transition = 'opacity 0.5s ease, transform 0.5s ease';
            alert.style.opacity = '0';
            alert.style.transform = 'translateY(-10px)';
            setTimeout(() => alert.remove(), 500);
        }, 5000);
    });

    // 2. Drag & Drop File Upload Handler
    const dropZone = document.getElementById('dropZone');
    const fileInput = document.getElementById('fileInput');
    const fileInfo = document.getElementById('fileInfo');
    const fileNameDisplay = document.getElementById('fileName');
    const fileSizeDisplay = document.getElementById('fileSize');

    if (dropZone && fileInput) {
        ['dragenter', 'dragover'].forEach(eventName => {
            dropZone.addEventListener(eventName, (e) => {
                e.preventDefault();
                e.stopPropagation();
                dropZone.classList.add('border-emerald');
                dropZone.style.borderColor = '#10b981';
                dropZone.style.background = 'rgba(16, 185, 129, 0.08)';
            }, false);
        });

        ['dragleave', 'drop'].forEach(eventName => {
            dropZone.addEventListener(eventName, (e) => {
                e.preventDefault();
                e.stopPropagation();
                dropZone.classList.remove('border-emerald');
                dropZone.style.borderColor = 'rgba(16, 185, 129, 0.2)';
                dropZone.style.background = 'transparent';
            }, false);
        });

        dropZone.addEventListener('drop', (e) => {
            const dt = e.dataTransfer;
            const files = dt.files;
            if (files.length > 0) {
                fileInput.files = files;
                updateFileInfo(files[0]);
            }
        });

        fileInput.addEventListener('change', () => {
            if (fileInput.files.length > 0) {
                updateFileInfo(fileInput.files[0]);
            }
        });

        function updateFileInfo(file) {
            if (fileInfo && fileNameDisplay && fileSizeDisplay) {
                fileInfo.style.display = 'block';
                fileNameDisplay.textContent = file.name;
                const sizeKb = (file.size / 1024).toFixed(1);
                const sizeMb = (file.size / (1024 * 1024)).toFixed(2);
                fileSizeDisplay.textContent = file.size > 1024 * 1024 ? `${sizeMb} MB` : `${sizeKb} KB`;
            }
        }
    }
});

// Clipboard copy helper for signed download URLs
function copyToClipboard(text, elementId) {
    navigator.clipboard.writeText(text).then(() => {
        const btn = document.getElementById(elementId);
        if (btn) {
            const originalText = btn.innerHTML;
            btn.innerHTML = '✓ Copied!';
            btn.style.background = '#059669';
            setTimeout(() => {
                btn.innerHTML = originalText;
                btn.style.background = '';
            }, 2000);
        }
    }).catch(err => {
        console.error('Failed to copy: ', err);
    });
}
