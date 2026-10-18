(() => {
    'use strict';
    const form = document.getElementById('assignment-form');
    if (!form) return;
    const byId = id => document.getElementById(id);
    const editing = form.dataset.edit === 'true';
    const saveLabel = editing ? 'Lưu thay đổi' : 'Lưu bản nháp';
    const saveButton = byId('save-draft');
    // Validate after filling the one explicitly allowed blank Objective points field.
    form.noValidate = true;
    const templates = JSON.parse(byId('template-data').value);
    const taskLimit = 200, questionLimit = 500, optionLimit = 10;
    let tasks, questions;
    try {
        tasks = JSON.parse(byId('tasks-data').value || '[]');
        questions = JSON.parse(byId('questions-data').value || '[]');
        if (!Array.isArray(tasks) || !Array.isArray(questions)) throw new Error('Invalid content');
        if (tasks.some(t => !t || typeof t.title !== 'string' || (t.description != null && typeof t.description !== 'string')
                    || (t.maximum_points != null && !['number', 'string'].includes(typeof t.maximum_points)))
                || questions.some(q => !q || typeof q.question_text !== 'string' || typeof q.maximum_points !== 'string'
                    || !Array.isArray(q.options) || q.options.some(o => !o || typeof o.option_text !== 'string'
                        || typeof o.is_correct !== 'boolean'))) throw new Error('Invalid content');
    } catch (_) {
        // Keep an invalid submitted payload intact for diagnosis; never silently erase it.
        byId('tasksJson-error').textContent = `Không tải được nội dung đã gửi. Hãy về danh sách và mở lại trang ${editing ? 'chỉnh sửa' : 'tạo bài tập'}.`;
        return;
    }
    const savedResponseType = form.dataset.savedResponseType || byId('responseType').value;
    const savedEssayContent = form.dataset.savedEssayContent === ''
        ? tasks.length > 0
        : form.dataset.savedEssayContent === 'true';
    const savedObjectiveContent = form.dataset.savedObjectiveContent === ''
        ? questions.length > 0
        : form.dataset.savedObjectiveContent === 'true';
    let dirty = Boolean(document.querySelector('.notice.error'));
    function wholeNumberText(value) {
        // Database numeric columns may return 10.00 for an integer score.
        const text = String(value ?? '');
        return /^[0-9]+\.0+$/.test(text) ? text.substring(0, text.indexOf('.')) : text;
    }
    byId('maximumScore').value = wholeNumberText(byId('maximumScore').value);
    tasks.forEach(task => {
        task.maximum_points = wholeNumberText(task.maximum_points);
        // Preserve text from older forms while presenting one question-content field.
        if (task.description?.trim() && task.description !== task.title) task.title += '\n' + task.description;
        delete task.description;
    });
    function newTask() { return {title: '', maximum_points: ''}; }
    function newQuestion() {
        return {question_id: 0, question_text: '', maximum_points: '', options: [
            {option_text: '', is_correct: false}, {option_text: '', is_correct: false}]};
    }
    function integerInput(input, minimum, maximum) {
        input.type = 'text'; input.inputMode = 'numeric'; input.pattern = '[0-9]+';
        input.dataset.integerMin = String(minimum); input.dataset.integerMax = String(maximum);
        // Reject an expression as a whole instead of silently turning 5+5 into 55.
        input.addEventListener('beforeinput', event => {
            if (event.data != null && !/^[0-9]*$/.test(event.data)) event.preventDefault();
        });
        input.addEventListener('paste', event => {
            if (!/^[0-9]*$/.test(event.clipboardData.getData('text'))) event.preventDefault();
        });
    }
    form.querySelectorAll('[data-integer-min]').forEach(input => {
        integerInput(input, input.dataset.integerMin, input.dataset.integerMax);
    });
    function pointsInCents(value) {
        if (!/^[0-9]+(?:\.[0-9]{1,2})?$/.test(value)) return null;
        const [whole, fraction = ''] = value.split('.');
        const cents = Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
        return Number.isSafeInteger(cents) && cents >= 1 && cents <= 10000 ? cents : null;
    }
    function formatPoints(cents) { return (cents / 100).toFixed(2); }
    function scoreInput(input) {
        input.type = 'text'; input.inputMode = 'decimal'; input.pattern = '[0-9]+([.][0-9]{1,2})?';
        input.dataset.points = 'true';
        input.addEventListener('beforeinput', event => {
            if (event.data != null && !/^[0-9.]*$/.test(event.data)) event.preventDefault();
        });
        input.addEventListener('paste', event => {
            if (!/^[0-9]+(?:\.[0-9]{0,2})?$/.test(event.clipboardData.getData('text'))) event.preventDefault();
        });
    }
    function pendingObjectivePoints() {
        const maximum = pointsInCents(byId('maximumScore').value);
        const blanks = questions.filter(question => question.maximum_points === '');
        if (maximum === null || blanks.length !== 1) return null;
        let total = 0;
        for (const question of questions) {
            if (question === blanks[0]) continue;
            const points = pointsInCents(question.maximum_points);
            if (points === null) return null;
            total += points;
        }
        return {question: blanks[0], index: questions.indexOf(blanks[0]), remaining: maximum - total};
    }
    function validateFields() {
        form.querySelectorAll('input, select, textarea').forEach(input => input.setCustomValidity(''));
        form.querySelectorAll('[data-integer-min]').forEach(input => {
            const value = input.value, minimum = Number(input.dataset.integerMin), maximum = Number(input.dataset.integerMax);
            const valid = /^[0-9]+$/.test(value) && Number(value) >= minimum && Number(value) <= maximum;
            input.setCustomValidity(!input.disabled && value !== '' && !valid
                ? `Nhập số nguyên từ ${minimum} đến ${maximum}.` : '');
        });
        form.querySelectorAll('[data-character-limit]').forEach(input => {
            const count = Array.from(input.value).length, maximum = Number(input.dataset.characterLimit);
            const counter = byId(`${input.id}-count`);
            counter.textContent = `${count} / ${maximum} ký tự`;
            counter.classList.toggle('over-limit', count > maximum);
            input.setCustomValidity(!input.disabled && count > maximum ? `Nhập tối đa ${maximum} ký tự.` : '');
        });
        const maximum = pointsInCents(byId('maximumScore').value);
        form.querySelectorAll('[data-points]').forEach(input => {
            const points = pointsInCents(input.value);
            input.setCustomValidity(input.value !== '' && (points === null || (maximum !== null && points > maximum))
                ? 'Nhập ít nhất 0.01 điểm, tối đa 2 chữ số thập phân và không vượt quá điểm tối đa của bài tập.' : '');
        });
        form.querySelectorAll('input, select, textarea').forEach(input => {
            if (!input.willValidate) return;
            if (input.required && !input.value.trim()) input.setCustomValidity('Vui lòng điền thông tin bắt buộc.');
            else if (input.validity.badInput) input.setCustomValidity('Vui lòng nhập giá trị hợp lệ.');
            else if (input.validity.patternMismatch && !input.validity.customError)
                input.setCustomValidity('Vui lòng nhập đúng định dạng được yêu cầu.');
        });
    }
    function sync() {
        // Preserve both editors on validation errors. The server saves only the selected type.
        questions.forEach(question => {
            if (question.question_id === undefined) question.question_id = 0;
            question.options.forEach((option, index) => { option.option_order = index + 1; });
        });
        const objective = byId('responseType').value === 'OBJECTIVE';
        byId('tasks-data').value = JSON.stringify(tasks);
        byId('questions-data').value = JSON.stringify(questions);
        const total = questions.reduce((sum, question) => sum + (pointsInCents(question.maximum_points) || 0), 0);
        byId('points-total').textContent = `Tổng điểm: ${formatPoints(total)} / ${byId('maximumScore').value || '—'}`;
        const taskTotal = tasks.reduce((sum, task) => sum + (pointsInCents(task.maximum_points) || 0), 0);
        byId('task-points-total').textContent = `Tổng điểm: ${formatPoints(taskTotal)} / ${byId('maximumScore').value || '—'}`;
        const pending = pendingObjectivePoints();
        byId('points-auto-fill').textContent = objective && pending && pending.remaining >= 1
            ? `Câu ${pending.index + 1} chưa có điểm và sẽ nhận ${formatPoints(pending.remaining)} điểm khi lưu.` : '';
        byId('task-count').textContent = `${tasks.length} / ${taskLimit} câu hỏi`;
        byId('question-count').textContent = `${questions.length} / ${questionLimit} câu hỏi`;
        byId('add-task').disabled = tasks.length >= taskLimit;
        byId('add-question').disabled = questions.length >= questionLimit;
        validateFields();
    }
    function changed() { dirty = true; byId('client-form-error').textContent = ''; sync(); }
    function node(tag, text, className) {
        const el = document.createElement(tag);
        if (text !== undefined) el.textContent = text;
        if (className) el.className = className;
        return el;
    }
    function button(text, action, disabled = false) {
        const el = node('button', text, 'button'); el.type = 'button'; el.disabled = disabled;
        el.addEventListener('click', action); return el;
    }
    function field(label, value, update, kind = 'textarea', required = false) {
        const wrap = node('label', label);
        const input = node(kind === 'points' ? 'input' : kind);
        if (kind === 'points') scoreInput(input);
        if (kind === 'textarea') input.rows = 2;
        input.value = value || ''; input.required = required;
        input.addEventListener('input', () => { update(input.value); changed(); });
        wrap.append(input); return wrap;
    }
    function controls(list, index, render, label, minimum = 0) {
        const actions = node('div', undefined, 'editor-actions');
        function move(offset) { [list[index], list[index + offset]] = [list[index + offset], list[index]]; changed(); render(); }
        actions.append(button('Chuyển lên', () => move(-1), index === 0),
            button('Chuyển xuống', () => move(1), index === list.length - 1),
            button('Xóa', () => {
                if (list.length > minimum && confirm(`Xóa ${label} này khỏi bản nháp?`)) {
                    list.splice(index, 1); changed(); render();
                }
            }, list.length <= minimum));
        return actions;
    }
    function renderTasks() {
        const editor = byId('task-editor'); editor.replaceChildren();
        if (!tasks.length) editor.append(node('p', 'Chưa có câu hỏi tự luận.', 'form-hint'));
        tasks.forEach((task, index) => {
            const card = node('article', undefined, 'editor-card');
            const heading = node('div', undefined, 'editor-heading');
            heading.append(node('strong', `Câu ${index + 1}`), controls(tasks, index, renderTasks, 'câu hỏi', 1));
            card.append(heading, field('Nội dung câu hỏi *', task.title, value => task.title = value, 'textarea', true),
                field('Điểm câu hỏi *', task.maximum_points, value => task.maximum_points = value, 'points', true));
            editor.append(card);
        });
        sync();
    }
    function renderQuestions() {
        const editor = byId('question-editor'); editor.replaceChildren();
        if (!questions.length) editor.append(node('p', 'Thêm ít nhất một câu hỏi trước khi lưu.', 'form-hint'));
        questions.forEach((question, index) => {
            const card = node('article', undefined, 'editor-card');
            const heading = node('div', undefined, 'editor-heading');
            heading.append(node('strong', `Câu ${index + 1}`), controls(questions, index, renderQuestions, 'câu hỏi', 1));
            card.append(heading, field('Nội dung câu hỏi *', question.question_text, value => question.question_text = value, 'textarea', true),
                field('Điểm câu hỏi *', question.maximum_points, value => question.maximum_points = value, 'points', true));
            question.options.forEach((option, optionIndex) => {
                const row = node('div', undefined, 'option-row');
                const correct = node('label', undefined, 'correct-choice');
                const checkbox = node('input'); checkbox.type = 'checkbox';
                checkbox.checked = option.is_correct === true;
                checkbox.addEventListener('change', () => { option.is_correct = checkbox.checked; changed(); });
                correct.append(checkbox, node('span', 'Đáp án đúng'));
                row.append(correct, field(`Lựa chọn ${optionIndex + 1} *`, option.option_text, value => option.option_text = value, 'input', true),
                    controls(question.options, optionIndex, renderQuestions, 'lựa chọn'));
                card.append(row);
            });
            const actions = node('div', undefined, 'editor-actions');
            actions.append(button('Thêm lựa chọn', () => {
                if (question.options.length < optionLimit) {
                    question.options.push({option_text: '', is_correct: false}); changed(); renderQuestions();
                }
            }, question.options.length >= optionLimit),
                button('Bỏ chọn tất cả đáp án đúng', () => { question.options.forEach(o => o.is_correct = false); changed(); renderQuestions(); }));
            card.append(node('p', `${question.options.length} / ${optionLimit} lựa chọn. Đánh dấu tất cả đáp án đúng.`, 'form-hint'), actions);
            editor.append(card);
        });
        sync();
    }
    let previousTemplate = byId('templateId').value;
    byId('templateId').addEventListener('change', () => {
        const select = byId('templateId');
        const template = templates.find(t => String(t.templateId) === select.value);
        if (!template) return;
        const hasContent = ['title', 'instructions', 'maximumScore'].some(id => byId(id).value.trim());
        if (hasContent && !confirm('Thay tiêu đề, hướng dẫn và điểm tối đa bằng dữ liệu của mẫu này? Nội dung khác vẫn được giữ lại.')) {
            select.value = previousTemplate; return;
        }
        byId('title').value = template.title;
        byId('instructions').value = template.instructions || '';
        byId('maximumScore').value = wholeNumberText(template.maximumScore);
        previousTemplate = select.value; changed();
    });
    function updateType() {
        const practice = byId('purpose').value === 'PRACTICE';
        const responseSelect = byId('responseType');
        const objectiveOption = responseSelect.querySelector('option[value="OBJECTIVE"]');
        if (practice) responseSelect.value = objectiveOption && !objectiveOption.disabled ? 'OBJECTIVE' : '';
        const essayOption = responseSelect.querySelector('option[value="ESSAY"]');
        if (essayOption) essayOption.disabled = practice;
        byId('attempt-field').hidden = !practice;
        byId('attemptLimit').disabled = !practice;
        byId('attemptLimit').required = practice;
        const objective = byId('responseType').value === 'OBJECTIVE';
        if (objective && questions.length === 0) { questions.push(newQuestion()); renderQuestions(); }
        if (!objective && tasks.length === 0) { tasks.push(newTask()); renderTasks(); }
        byId('essay-section').hidden = objective;
        byId('essay-fields').disabled = objective;
        byId('question-section').hidden = !objective;
        byId('question-fields').disabled = !objective;
    }
    byId('purpose').addEventListener('change', () => { updateType(); changed(); });
    byId('responseType').addEventListener('change', () => {
        updateType(); changed();
    });
    byId('add-task').addEventListener('click', () => {
        if (tasks.length < taskLimit) { tasks.push(newTask()); changed(); renderTasks(); }
    });
    byId('add-question').addEventListener('click', () => {
        if (questions.length < questionLimit) { questions.push(newQuestion()); changed(); renderQuestions(); }
    });
    form.addEventListener('input', changed);
    form.addEventListener('change', () => { dirty = true; });
    window.addEventListener('beforeunload', event => { if (dirty) { event.preventDefault(); event.returnValue = ''; } });
    document.querySelectorAll('.leave-form').forEach(link => link.addEventListener('click', event => {
        if (dirty && !confirm('Rời trang và bỏ các thay đổi chưa lưu?')) event.preventDefault();
        else dirty = false;
    }));
    form.addEventListener('submit', event => {
        if (saveButton.dataset.canSave !== 'true' || form.dataset.editConflict === 'true') {
            event.preventDefault(); return;
        }
        const responseType = byId('responseType').value;
        function reject(message) {
            event.preventDefault(); byId('client-form-error').textContent = message; byId('client-form-error').focus();
        }
        if (responseType === 'OBJECTIVE') {
            const pending = pendingObjectivePoints();
            if (pending) {
                if (pending.remaining < 1) {
                    reject('Câu hỏi chưa có điểm phải nhận được ít nhất 0.01 điểm còn lại. Hãy điều chỉnh điểm các câu khác trước.'); return;
                }
                pending.question.maximum_points = formatPoints(pending.remaining);
                renderQuestions();
            }
        }
        sync();
        if (!form.reportValidity()) { event.preventDefault(); return; }
        const selected = responseType === 'OBJECTIVE' ? questions : tasks;
        const limit = responseType === 'OBJECTIVE' ? questionLimit : taskLimit;
        if (selected.length < 1 || selected.length > limit) {
            reject(`Thêm từ 1 đến ${limit} câu hỏi.`); return;
        }
        const total = selected.reduce((sum, item) => sum + (pointsInCents(item.maximum_points) || 0), 0);
        if (total !== pointsInCents(byId('maximumScore').value)) {
            reject('Tổng điểm các câu hỏi phải bằng điểm tối đa của bài tập.'); return;
        }
        const removingSavedContent = editing && responseType !== savedResponseType
            && ((savedResponseType === 'ESSAY' && savedEssayContent)
                || (savedResponseType === 'OBJECTIVE' && savedObjectiveContent));
        if (removingSavedContent) {
            const removedContent = savedResponseType === 'ESSAY' ? 'câu hỏi tự luận' : 'câu hỏi trắc nghiệm và các lựa chọn';
            const selectedType = responseType === 'OBJECTIVE' ? 'trắc nghiệm' : 'tự luận';
            if (!confirm(`Lưu dưới dạng ${selectedType}? Thao tác này sẽ xóa ${removedContent} đang lưu trong bản nháp.`)) {
                event.preventDefault(); return;
            }
        }
        dirty = false;
        saveButton.disabled = true;
        saveButton.textContent = 'Đang lưu…';
    });
    renderTasks(); renderQuestions(); updateType(); sync();
    saveButton.disabled = saveButton.dataset.canSave !== 'true';
    window.addEventListener('pageshow', () => {
        saveButton.disabled = saveButton.dataset.canSave !== 'true';
        saveButton.textContent = saveLabel;
    });
    byId('form-errors')?.focus();
})();
