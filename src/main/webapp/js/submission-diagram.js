(() => {
    'use strict';
    const buttons = document.querySelectorAll('[data-diagram-zoom]');
    if (!buttons.length) return;
    const dialog = document.createElement('dialog');
    dialog.className = 'submission-diagram-dialog';
    dialog.setAttribute('aria-label', 'Xem sơ đồ bài làm');
    const close = document.createElement('button');
    close.type = 'button';
    close.textContent = 'Đóng sơ đồ';
    const image = document.createElement('img');
    const stage = document.createElement('div');
    stage.className = 'submission-diagram-stage';
    stage.append(image);
    dialog.append(close, stage);
    document.body.append(dialog);
    let opener;
    close.addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', () => {
        image.removeAttribute('src');
        if (opener) opener.focus();
    });
    buttons.forEach(button => button.addEventListener('click', () => {
        const original = button.closest('figure')?.querySelector('img');
        if (!original || !original.src.startsWith('data:image/png;base64,')) return;
        opener = button;
        image.src = original.src;
        image.alt = original.alt;
        dialog.showModal();
    }));
})();
