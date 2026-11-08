(() => {
    'use strict';

    const approved = new WeakMap();
    let pending = null;
    let dialog;
    let confirmButton;
    let message;
    let heading;

    const fingerprint = form => new URLSearchParams(new FormData(form)).toString();

    function notify(text) {
        let notice = document.getElementById('publish-confirmation-notice');
        if (!notice) {
            notice = document.createElement('p');
            notice.id = 'publish-confirmation-notice';
            notice.className = 'notice error';
            notice.setAttribute('role', 'alert');
            notice.tabIndex = -1;
            (document.getElementById('page-content') || document.body).prepend(notice);
        }
        notice.textContent = text;
        notice.focus();
    }

    function createDialog() {
        dialog = document.createElement('dialog');
        dialog.className = 'publish-confirmation-dialog';
        dialog.setAttribute('aria-labelledby', 'publish-confirmation-heading');
        dialog.setAttribute('aria-describedby', 'publish-confirmation-message');
        heading = document.createElement('h2');
        heading.id = 'publish-confirmation-heading';
        message = document.createElement('p');
        message.id = 'publish-confirmation-message';
        const actions = document.createElement('div');
        actions.className = 'publish-confirmation-actions';
        const cancel = document.createElement('button');
        cancel.type = 'button';
        cancel.className = 'button';
        cancel.textContent = 'Không, quay lại';
        cancel.addEventListener('click', () => dialog.close());
        confirmButton = document.createElement('button');
        confirmButton.type = 'button';
        confirmButton.className = 'button primary';
        confirmButton.textContent = 'Có, công bố';
        actions.append(cancel, confirmButton);
        dialog.append(heading, message, actions);
        document.body.append(dialog);

        dialog.addEventListener('close', () => { pending = null; });
        confirmButton.addEventListener('click', () => {
            const review = pending;
            if (!review) return;
            pending = null;
            dialog.close();

            if (!review.form.isConnected || review.submitter.disabled) return;
            if (fingerprint(review.form) !== review.fingerprint) {
                notify('Nội dung đã thay đổi trong lúc xác nhận. Hãy kiểm tra lại rồi nhấn Công bố.');
                return;
            }

            approved.set(review.form, review.submitter);
            review.form.requestSubmit(review.submitter);
        });
        return cancel;
    }

    // Capture before editor handlers change the form or disable its publish button.
    document.addEventListener('submit', event => {
        const form = event.target;
        const submitter = event.submitter || (form.id === 'lifecycle-form'
            ? form.querySelector('[data-confirm-publish]') : null);
        if (!submitter?.hasAttribute('data-confirm-publish')) return;

        // The full lifecycle window already presents the publication confirmation.
        if (form.closest('dialog.assignment-confirm-dialog[open]')) return;

        if (approved.get(form) === submitter) {
            approved.delete(form);
            return;
        }

        event.preventDefault();
        event.stopImmediatePropagation();
        // Custom-validated editors may fill pending points in their submit handler.
        // Native validation here would prevent that handler from running at all.
        if ((!form.noValidate && !form.reportValidity()) || form.dataset.submitting === 'true') return;

        let cancel;
        if (!dialog) cancel = createDialog();
        if (typeof dialog.showModal !== 'function') {
            notify('Trình duyệt chưa hỗ trợ cửa sổ xác nhận. Hãy dùng trình duyệt mới để công bố.');
            return;
        }

        const evaluation = submitter.dataset.confirmPublish === 'evaluation';
        heading.textContent = evaluation ? 'Xác nhận công bố điểm' : 'Xác nhận công bố bài tập';
        message.textContent = evaluation
            ? 'Bạn có muốn công bố điểm và nhận xét này cho sinh viên không?'
            : 'Bạn có muốn công bố bài tập cho các lớp đã chọn không? Nội dung bài tập sẽ được khóa sau khi công bố.';
        pending = {form, submitter, fingerprint: fingerprint(form)};
        if (!dialog.open) dialog.showModal();
        (cancel || dialog.querySelector('button')).focus();
    }, true);
})();
