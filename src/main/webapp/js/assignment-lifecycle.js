(() => {
    const links = document.querySelectorAll('[data-lifecycle-dialog]');
    if (!links.length) return;

    const dialog = document.createElement('dialog');
    dialog.className = 'assignment-confirm-dialog';
    dialog.setAttribute('aria-label', 'Xác nhận thao tác bài tập');
    const dismiss = document.createElement('button');
    dismiss.type = 'button';
    dismiss.className = 'dialog-dismiss';
    dismiss.textContent = 'Đóng';
    dismiss.addEventListener('click', () => dialog.close());
    const body = document.createElement('div');
    body.setAttribute('aria-live', 'polite');
    dialog.append(dismiss, body);
    document.body.append(dialog);
    let loading;

    for (const link of links) {
        link.addEventListener('click', async event => {
            event.preventDefault();
            loading?.abort();
            loading = new AbortController();
            const current = loading;
            body.textContent = 'Đang tải thông tin xác nhận…';
            if (!dialog.open) dialog.showModal();

            try {
                if (link.dataset.closeTemplate) {
                    body.replaceChildren(document.getElementById(link.dataset.closeTemplate).content.cloneNode(true));
                    return;
                }

                const response = await fetch(link.href, {
                    credentials: 'same-origin', signal: current.signal,
                    headers: {'X-Requested-With': 'XMLHttpRequest'}
                });
                const html = new DOMParser().parseFromString(await response.text(), 'text/html');
                const panel = html.querySelector('.lifecycle-panel');
                if (!response.ok || !panel) throw new Error('Không tải được xác nhận. Hãy kiểm tra phiên đăng nhập và thử lại.');
                if (loading !== current || !dialog.open) return;

                body.replaceChildren(document.importNode(panel, true));
                for (const back of body.querySelectorAll('.lifecycle-actions a')) {
                    if (back.textContent.includes('Quay về')) back.addEventListener('click', e => {
                        e.preventDefault();
                        dialog.close();
                    });
                }
            } catch (error) {
                if (error.name !== 'AbortError') body.textContent = error.message;
            }
        });
    }

    dialog.addEventListener('submit', event => {
        const form = event.target;
        if (form.dataset.submitting) {
            event.preventDefault();
            return;
        }

        form.dataset.submitting = 'true';
        const submit = event.submitter;
        if (submit) {
            submit.setAttribute('aria-disabled', 'true');
            submit.textContent = 'Đang xử lý…';
        }
    });

    dialog.addEventListener('close', () => loading?.abort());
})();
