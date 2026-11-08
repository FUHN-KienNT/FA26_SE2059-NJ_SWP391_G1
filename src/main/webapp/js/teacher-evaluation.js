(() => {
    'use strict';
    const form = document.querySelector('[data-evaluation-form]');
    const error = document.querySelector('[data-evaluation-error]');

    if (error) error.focus();

    const tabs = [...document.querySelectorAll('[data-grade-tab]')];
    const notice = document.getElementById('evaluation-notification');

    function announce(message, failed = false) {
        if (!notice) return;
        notice.hidden = false;
        notice.textContent = message;
        notice.classList.toggle('evaluation-error', failed);
        notice.classList.toggle('evaluation-success', !failed);
        notice.setAttribute('role', failed ? 'alert' : 'status');
        notice.focus();
    }

    function selectTab(key, focus = false) {
        for (const tab of tabs) {
            const active = tab.dataset.gradeTab === key;
            tab.setAttribute('aria-selected', String(active));
            tab.tabIndex = active ? 0 : -1;
            if (active && focus) tab.focus();
        }

        document.querySelectorAll('[data-answer-panel]').forEach(panel => {
            panel.hidden = panel.dataset.answerPanel !== key;
        });
        document.querySelectorAll('[data-score-panel]').forEach(panel => {
            panel.hidden = panel.dataset.scorePanel !== key;
        });
    }

    tabs.forEach((tab, index) => {
        tab.addEventListener('click', () => selectTab(tab.dataset.gradeTab));
        tab.addEventListener('keydown', event => {
            let next;
            if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
            else if (event.key === 'ArrowLeft') next = (index + tabs.length - 1) % tabs.length;
            else if (event.key === 'Home') next = 0;
            else if (event.key === 'End') next = tabs.length - 1;
            else return;

            event.preventDefault();
            selectTab(tabs[next].dataset.gradeTab, true);
        });
    });

    // Reading answers remains available while the grading form is locked before closure.
    if (!form) return;

    const scores = [...document.querySelectorAll('[data-evaluation-score]')];
    const total = document.querySelector('[data-evaluation-total]');
    const progress = document.querySelector('[data-evaluation-progress]');
    const savedFields = [...form.elements]
        .filter(field => field.hasAttribute('data-saved-value'))
        .map(field => ({ field, value: field.dataset.savedValue }));

    function markAi(textarea, generated) {
        const name = textarea.dataset.aiFeedback;
        if (!name) return;
        const field = form.elements.namedItem(name);
        if (field) field.value = String(generated);
        textarea.classList.toggle('ai-feedback', generated);
        document.querySelectorAll('[data-ai-label]').forEach(label => {
            if (label.dataset.aiLabel === name) label.hidden = !generated;
        });
    }

    document.querySelectorAll('[data-ai-feedback]').forEach(textarea => {
        textarea.addEventListener('input', () => markAi(textarea, false));
    });

    // Sum integer hundredths, preserving the distinction between zero and an empty score.
    const hundredths = value => {
        if (!/^[0-9]{1,3}(\.[0-9]{1,2})?$/.test(value)) return null;

        const [whole, fraction = ''] = value.split('.');
        return Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
    };

    const validate = input => {
        const amount = hundredths(input.value);
        const maximum = hundredths(input.dataset.maximum);
        const invalid = input.value !== '' && (amount === null || amount > maximum);
        input.setCustomValidity(invalid ? `Nhập điểm từ 0 đến ${input.dataset.maximum}, có tối đa 2 chữ số thập phân.` :
            '');
        return invalid;
    };

    const update = () => {
        let sum = 0;
        let count = 0;
        let invalid = false;

        for (const input of scores) {
            if (validate(input)) invalid = true;
            else if (input.value !== '') {
                sum += hundredths(input.value);
                count++;
            }
        }

        if (scores.length && total) total.textContent = invalid ? 'Kiểm tra điểm' : (sum / 100).toFixed(2).replace(
            /\.?0+$/, '');

        if (scores.length && progress) progress.textContent =
            `Đã chấm ${count}/${progress.dataset.requirementCount} yêu cầu`;

        if (scores.length) document.querySelectorAll('[data-question-total]').forEach(output => {
            const taskScores = scores.filter(input => input.name.startsWith(
                `score_${output.dataset.questionTotal}_`));
            const entered = taskScores.filter(input => input.value !== '');
            output.textContent = entered.some(validate) ? 'Kiểm tra điểm' : entered.length === 0 ? 'Chưa chấm' :
                (entered.reduce((sum, input) => sum + hundredths(input.value), 0) / 100).toFixed(2).replace(
                    /\.?0+$/, '');
        });
    };

    scores.forEach(input => input.addEventListener('input', update));

    // Clear action-specific errors before native validation so an incomplete completion
    // can still be saved as a draft.
    document.querySelectorAll('button[form="evaluation-form"][name="action"]').forEach(button => {
        button.addEventListener('click', () => {
            scores.forEach(validate);
            const invalid = scores.find(input => !input.validity.valid);
            if (invalid?.dataset.key) selectTab(invalid.dataset.key);
        });
    });

    form.addEventListener('submit', event => {
        const action = event.submitter?.value;

        if (!action) {
            event.preventDefault();
            return;
        }

        if (['completeEvaluation', 'publishEvaluation', 'reviseReleasedEvaluation'].includes(action)) {
            const missing = scores.find(input => input.value === '');

            if (missing) {
                event.preventDefault();
                if (missing.dataset.key) selectTab(missing.dataset.key);
                missing.setCustomValidity('Nhập điểm cho yêu cầu này trước khi hoàn tất chấm. Có thể nhập 0.');
                missing.reportValidity();
                return;
            }
        }

        if (form.dataset.submitting === 'true') {
            event.preventDefault();
            return;
        }
        form.dataset.submitting = 'true';
    });

    document.querySelector('[data-reset-evaluation]')?.addEventListener('click', () => {
        if (!confirm('Khôi phục điểm và nhận xét đã lưu gần nhất? Các chỉnh sửa chưa lưu sẽ được bỏ.')) return;
        for (const saved of savedFields) {
            saved.field.value = saved.value;
            saved.field.setCustomValidity('');
        }
        form.dataset.submitting = 'false';
        document.querySelectorAll('[data-ai-feedback]').forEach(textarea => {
            markAi(textarea, form.elements.namedItem(textarea.dataset.aiFeedback)?.value === 'true');
        });
        update();
        announce('Đã khôi phục điểm và nhận xét đã lưu gần nhất.');
    });

    const restoreDialog = document.getElementById('restore-evaluation-dialog');
    document.querySelectorAll('[data-restore-history]').forEach(button => {
        button.addEventListener('click', () => {
            document.getElementById('restore-history-id').value = button.dataset.restoreHistory;
            restoreDialog.showModal();
        });
    });
    document.querySelector('[data-close-restore]')?.addEventListener('click', () => restoreDialog.close());

    const aiButton = document.querySelector('[data-ai-grade]');
    aiButton?.addEventListener('click', async () => {
        if (aiButton.disabled) return;
        const original = new URLSearchParams(new FormData(form)).toString();
        const data = new FormData(form);
        data.set('action', 'aiEvaluation');
        aiButton.disabled = true;
        aiButton.textContent = 'AI đang chấm…';
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 75000);

        try {
            // The form-associated submit buttons are named action and can shadow form.action.
            const response = await fetch(form.getAttribute('action'), {
                method: 'POST', body: new URLSearchParams(data),
                credentials: 'same-origin', signal: controller.signal
            });

            if (response.redirected || response.status === 401)
                throw new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại trước khi thử chấm bằng AI.');

            if (response.status === 403)
                throw new Error('Phiên biểu mẫu không hợp lệ hoặc bạn chưa có quyền chấm bài. Hãy kiểm tra đăng nhập và tải lại trang.');

            if (!response.headers.get('content-type')?.toLowerCase().includes('application/json'))
                throw new Error('Máy chủ chưa trả được đề xuất AI. Vui lòng thử lại sau khi kiểm tra kết nối.');

            let result;

            try {
                result = await response.json();
            } catch {
                throw new Error('Không đọc được đề xuất AI từ máy chủ. Chưa có điểm nào được áp dụng, hãy thử lại.');
            }

            if (!result || typeof result !== 'object')
                throw new Error('Đề xuất AI chưa có dữ liệu hợp lệ. Vui lòng thử lại.');

            if (!response.ok) throw new Error(result.error || 'AI chưa chấm được bài. Hãy thử lại.');
            if (original !== new URLSearchParams(new FormData(form)).toString())
                throw new Error('Bạn đã thay đổi biểu mẫu trong lúc AI chấm. Đề xuất chưa được áp dụng để giữ nội dung vừa sửa.');
            if (!Array.isArray(result.marks) || result.marks.length !== scores.length)
                throw new Error('Đề xuất AI thiếu điểm thành phần. Chưa áp dụng dữ liệu.');

            for (const mark of result.marks) {
                const key = `${mark.task_order}_${mark.requirement_order}`;
                const score = form.elements.namedItem(`score_${key}`);
                const feedback = form.elements.namedItem(`requirementFeedback_${key}`);
                if (!score || !feedback) throw new Error('Đề xuất AI không khớp yêu cầu của bài nộp.');
            }

            for (const mark of result.marks) {
                const key = `${mark.task_order}_${mark.requirement_order}`;
                form.elements.namedItem(`score_${key}`).value = String(mark.score);
                const feedback = form.elements.namedItem(`requirementFeedback_${key}`);
                feedback.value = mark.feedback;
                markAi(feedback, true);
            }
            const overall = form.elements.namedItem('feedback');
            overall.value = result.feedback || '';
            markAi(overall, true);
            update();
            announce(result.message || 'Đã điền đề xuất AI. Hãy kiểm tra rồi lưu hoặc công bố điểm.');
        } catch (failure) {
            announce(failure.name === 'AbortError' ? 'AI xử lý quá thời gian cho phép. Hãy thử lại.' : failure.message, true);
        } finally {
            clearTimeout(timeout);
            aiButton.disabled = false;
            aiButton.textContent = 'Chấm bằng AI';
        }
    });

    update();
})();
