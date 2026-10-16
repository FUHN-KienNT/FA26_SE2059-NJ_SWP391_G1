(() => {
    'use strict';
    const form = document.getElementById('objective-work');
    if (!form) return;
    const clock = document.getElementById('work-countdown');
    const status = document.getElementById('work-save-status');
    let deadline = Number(form.dataset.deadline);
    // performance.now is monotonic: changing the device clock does not extend the attempt.
    let serverTime = Number(form.dataset.serverTime);
    let syncedAt = performance.now();
    let dirty = true, saving = false, submitting = false, conflicted = false, debounce, submissionPayload;
    const now = () => serverTime + performance.now() - syncedAt;
    const resultUrl = () => `${form.action}?assignmentId=${encodeURIComponent(form.elements.assignmentId.value)}&submissionId=${encodeURIComponent(form.elements.submissionId.value)}`;
    const body = action => {
        const data = new URLSearchParams(new FormData(form));
        data.set('action', action);
        return data;
    };
    const lockInputs = () => form.querySelectorAll('input[name^="answer_"],button').forEach(input => { input.disabled = true; });
    function conflict() {
        conflicted = true; submitting = true; dirty = false; clearTimeout(debounce); lockInputs();
        status.textContent = 'Đáp án đã thay đổi ở phiên khác hoặc lần lưu trước chưa được xác nhận. Hãy tải lại để xem bản đã lưu. ';
        const reload = document.createElement('a'); reload.href = resultUrl(); reload.textContent = 'Tải lại bài làm'; status.append(reload);
    }

    async function save() {
        if (saving || submitting || !dirty) return;
        if (now() >= deadline) { finish(); return; }
        saving = true; dirty = false;
        status.textContent = 'Đang lưu đáp án...';
        const started = performance.now();
        try {
            const response = await fetch(form.action, {method: 'POST', body: body('save'), credentials: 'same-origin', cache: 'no-store'});
            if (response.status === 409) { conflict(); return; }
            if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) throw new Error('save');
            const saved = await response.json();
            serverTime = saved.serverTime + (performance.now() - started) / 2;
            syncedAt = performance.now(); deadline = saved.deadline;
            form.elements.answerRevision.value = saved.revision;
            if (saved.finished) { submitting = true; location.assign(resultUrl()); return; }
            status.textContent = 'Đã lưu đáp án.';
        } catch (error) {
            dirty = true;
            status.textContent = 'Chưa lưu được đáp án. Đang thử lại; hãy giữ kết nối mạng.';
        } finally {
            saving = false;
            // Only one request is in flight; a slower, older answer cannot overwrite a newer selection.
            if (dirty && !submitting && now() < deadline) debounce = setTimeout(save, 500);
        }
    }

    async function finish() {
        if (submitting) return;
        submitting = true;
        clearTimeout(debounce);
        // Preserve the payload across retries; disabled inputs are omitted by FormData.
        submissionPayload ??= body('submit');
        lockInputs();
        status.textContent = now() >= deadline ? 'Đã hết giờ. Đang tự động nộp bài...' : 'Đang nộp bài...';
        // A manual submit waits for its own in-flight autosave so it uses the resulting revision.
        while (saving && now() < deadline) await new Promise(resolve => setTimeout(resolve, 25));
        if (conflicted) return;
        submissionPayload.set('answerRevision', form.elements.answerRevision.value);
        try {
            const response = await fetch(form.action, {method: 'POST', body: submissionPayload, credentials: 'same-origin', cache: 'no-store'});
            if (response.status === 409) { conflict(); return; }
            if (!response.ok) throw new Error('submit');
            location.assign(resultUrl());
        } catch (error) {
            // The server worker still finalizes the last saved answers when this tab is offline/closed.
            status.textContent = 'Chưa nhận được xác nhận. Máy chủ sẽ tự nộp các đáp án đã lưu khi hết giờ. Đang thử lại...';
            setTimeout(() => { submitting = false; finish(); }, 3000);
        }
    }
    form.addEventListener('change', event => {
        if (!event.target.name?.startsWith('answer_') || submitting) return;
        dirty = true; clearTimeout(debounce);
        debounce = setTimeout(save, 100);
    });
    form.addEventListener('submit', event => { event.preventDefault(); finish(); });
    window.addEventListener('online', () => { if (now() >= deadline) finish(); else save(); });
    function tick() {
        const seconds = Math.max(0, Math.ceil((deadline - now()) / 1000));
        const hours = Math.floor(seconds / 3600), minutes = Math.floor(seconds % 3600 / 60);
        clock.textContent = `${String(hours).padStart(2,'0')}:${String(minutes).padStart(2,'0')}:${String(seconds % 60).padStart(2,'0')}`;
        clock.parentElement.classList.toggle('is-ending', seconds <= 60);
        if (!seconds) finish();
    }
    tick(); save(); setInterval(tick, 250);
})();
