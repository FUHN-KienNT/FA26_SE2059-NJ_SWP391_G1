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
    const taskLimit = 200, requirementLimit = 10, questionLimit = 500, optionLimit = 10;
    const answerTypes = {TEXT: 'Văn bản', DIAGRAM: 'Sơ đồ', BOTH: 'Văn bản và sơ đồ'};
    function normalizeTasks(items) {
        if (!Array.isArray(items) || items.length > taskLimit) throw new Error('Invalid questions');
        return items.map((task, index) => {
            if (!task || typeof task.title !== 'string' || (task.description != null && typeof task.description !== 'string')
                    || (task.maximum_points != null && !['number', 'string'].includes(typeof task.maximum_points)))
                throw new Error('Invalid question');
            const points = wholeNumberText(task.maximum_points);
            const title = task.title + (task.description?.trim() && task.description !== task.title ? '\n' + task.description : '');
            const requirements = task.requirements == null
                ? [{title: 'Trả lời câu hỏi', maximum_points: points, answer_type: 'TEXT'}] : task.requirements;
            if (!Array.isArray(requirements) || requirements.length < 1 || requirements.length > requirementLimit)
                throw new Error('Invalid requirements');
            return {task_order: index + 1, title, description: '', maximum_points: points,
                requirements: requirements.map((requirement, requirementIndex) => {
                    if (!requirement || typeof requirement.title !== 'string'
                            || !['number', 'string'].includes(typeof requirement.maximum_points)
                            || !Object.hasOwn(answerTypes, requirement.answer_type)) throw new Error('Invalid requirement');
                    return {requirement_order: requirementIndex + 1, title: requirement.title,
                        maximum_points: wholeNumberText(requirement.maximum_points), answer_type: requirement.answer_type};
                })};
        });
    }
    let tasks, questions;
    try {
        tasks = normalizeTasks(JSON.parse(byId('tasks-data').value || '[]'));
        questions = JSON.parse(byId('questions-data').value || '[]');
        if (!Array.isArray(questions)) throw new Error('Invalid content');
        if (questions.some(q => !q || typeof q.question_text !== 'string' || typeof q.maximum_points !== 'string'
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
    let authoringTab = 'manual';
    const previews = {import: null, generate: null};
    const requests = {import: false, generate: false};
    function wholeNumberText(value) {
        // Database numeric columns may return 10.00 for an integer score.
        const text = String(value ?? '');
        return /^[0-9]+\.0+$/.test(text) ? text.substring(0, text.indexOf('.')) : text;
    }
    byId('maximumScore').value = wholeNumberText(byId('maximumScore').value);
    function newRequirement() { return {title: '', maximum_points: '', answer_type: 'TEXT'}; }
    function newTask() { return {title: '', description: '', maximum_points: '', requirements: [newRequirement()]}; }
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
        value = String(value ?? '');
        if (!/^[0-9]+(?:\.[0-9]{1,2})?$/.test(value)) return null;
        const [whole, fraction = ''] = value.split('.');
        const cents = Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
        return Number.isSafeInteger(cents) && cents >= 1 && cents <= 10000 ? cents : null;
    }
    function formatPoints(cents) { return (cents / 100).toFixed(2); }
    function scoreInput(input, preview = false) {
        input.type = 'text'; input.inputMode = 'decimal';
        if (!preview) { input.pattern = '[0-9]+([.][0-9]{1,2})?'; input.dataset.points = 'true'; }
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
            const parent = input.dataset.parentTask === undefined ? null : pointsInCents(tasks[Number(input.dataset.parentTask)]?.maximum_points);
            input.setCustomValidity(input.value !== '' && (points === null || (maximum !== null && points > maximum))
                ? 'Nhập ít nhất 0.01 điểm, tối đa 2 chữ số thập phân và không vượt quá điểm tối đa của bài tập.' : '');
            if (points !== null && parent !== null && points > parent)
                input.setCustomValidity('Điểm yêu cầu con không được vượt điểm câu hỏi.');
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
        tasks.forEach((task, index) => {
            task.task_order = index + 1; task.description = '';
            task.requirements.forEach((requirement, requirementIndex) => { requirement.requirement_order = requirementIndex + 1; });
            const total = task.requirements.reduce((sum, requirement) => sum + (pointsInCents(requirement.maximum_points) || 0), 0);
            const summary = byId(`task-requirement-total-${index}`);
            if (summary) {
                summary.textContent = `${task.requirements.length} / ${requirementLimit} yêu cầu con · Tổng điểm: ${formatPoints(total)} / ${task.maximum_points || '—'}`;
                summary.classList.toggle('over-limit', pointsInCents(task.maximum_points) !== null && total > pointsInCents(task.maximum_points));
            }
        });
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
        byId('task-points-total').classList.toggle('over-limit', pointsInCents(byId('maximumScore').value) !== null && taskTotal > pointsInCents(byId('maximumScore').value));
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
    function field(label, value, update, kind = 'textarea', required = false, preview = false) {
        const wrap = node('label', label);
        const input = node(kind === 'points' ? 'input' : kind);
        if (kind === 'points') scoreInput(input, preview);
        if (kind === 'textarea') input.rows = 2;
        input.value = value || ''; input.required = required && !preview;
        input.addEventListener('input', () => { update(input.value); preview ? previewChanged() : changed(); });
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
        Array.from(actions.children).forEach(action => action.setAttribute('aria-label', `${action.textContent} ${label} ${index + 1}`));
        return actions;
    }
    function renderEssayEditor(editor, list, preview = false) {
        editor.replaceChildren();
        const render = preview ? renderPreview : renderTasks;
        if (!list.length) editor.append(node('p', 'Chưa có câu hỏi tự luận.', 'form-hint'));
        list.forEach((task, index) => {
            const card = node('article', undefined, 'editor-card');
            const heading = node('div', undefined, 'editor-heading');
            heading.append(node('strong', `Câu ${index + 1}`), controls(list, index, render, 'câu hỏi', 1));
            card.append(heading, field('Nội dung câu hỏi *', task.title, value => task.title = value, 'textarea', true, preview),
                field('Điểm câu hỏi *', task.maximum_points, value => task.maximum_points = value, 'points', true, preview));
            card.append(node('h3', 'Yêu cầu con'));
            task.requirements.forEach((requirement, requirementIndex) => {
                const requirementCard = node('section', undefined, 'requirement-card');
                const requirementHeading = node('div', undefined, 'editor-heading');
                requirementHeading.append(node('strong', `Yêu cầu ${requirementIndex + 1}`),
                    controls(task.requirements, requirementIndex, render, `yêu cầu con của câu ${index + 1}`, 1));
                const pointsField = field('Điểm yêu cầu *', requirement.maximum_points,
                    value => requirement.maximum_points = value, 'points', true, preview);
                if (!preview) pointsField.querySelector('input').dataset.parentTask = String(index);
                const answer = node('label', 'Hình thức trả lời *');
                const select = node('select');
                Object.entries(answerTypes).forEach(([value, label]) => {
                    const option = node('option', label); option.value = value; select.append(option);
                });
                select.value = requirement.answer_type;
                select.addEventListener('change', () => { requirement.answer_type = select.value; preview ? previewChanged() : changed(); });
                answer.append(select);
                const grid = node('div', undefined, 'form-grid'); grid.append(pointsField, answer);
                requirementCard.append(requirementHeading, field('Nội dung yêu cầu *', requirement.title,
                    value => requirement.title = value, 'textarea', true, preview), grid);
                card.append(requirementCard);
            });
            const summary = node('p', undefined, 'form-hint requirement-summary');
            if (!preview) summary.id = `task-requirement-total-${index}`;
            else {
                const total = task.requirements.reduce((sum, requirement) => sum + (pointsInCents(requirement.maximum_points) || 0), 0);
                summary.textContent = `${task.requirements.length} / ${requirementLimit} yêu cầu con · Tổng điểm: ${formatPoints(total)} / ${task.maximum_points || '—'}`;
            }
            card.append(summary, button('Thêm yêu cầu con', () => {
                if (task.requirements.length < requirementLimit) {
                    task.requirements.push(newRequirement()); preview ? previewChanged() : changed(); render();
                }
            }, task.requirements.length >= requirementLimit));
            editor.append(card);
        });
    }
    function renderTasks() {
        renderEssayEditor(byId('task-editor'), tasks);
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
    function essayError(list) {
        if (list.length < 1 || list.length > taskLimit) return `Thêm từ 1 đến ${taskLimit} câu hỏi tự luận.`;
        const maximum = pointsInCents(byId('maximumScore').value);
        if (maximum === null || !/^[0-9]+$/.test(byId('maximumScore').value)) return 'Nhập điểm tối đa của bài tập là số nguyên từ 1 đến 100.';
        let total = 0;
        for (const [index, task] of list.entries()) {
            if (!task.title.trim()) return `Câu ${index + 1}: nhập nội dung câu hỏi.`;
            const points = pointsInCents(task.maximum_points);
            if (points === null || points > maximum) return `Câu ${index + 1}: điểm phải từ 0.01 đến điểm tối đa của bài tập, tối đa 2 chữ số thập phân.`;
            if (task.requirements.length < 1 || task.requirements.length > requirementLimit)
                return `Câu ${index + 1}: thêm từ 1 đến ${requirementLimit} yêu cầu con.`;
            let requirementTotal = 0;
            for (const [requirementIndex, requirement] of task.requirements.entries()) {
                if (!requirement.title.trim()) return `Câu ${index + 1}, yêu cầu ${requirementIndex + 1}: nhập nội dung yêu cầu.`;
                const requirementPoints = pointsInCents(requirement.maximum_points);
                if (requirementPoints === null) return `Câu ${index + 1}, yêu cầu ${requirementIndex + 1}: nhập ít nhất 0.01 điểm, tối đa 2 chữ số thập phân.`;
                if (!Object.hasOwn(answerTypes, requirement.answer_type)) return `Câu ${index + 1}, yêu cầu ${requirementIndex + 1}: chọn hình thức trả lời hợp lệ.`;
                requirementTotal += requirementPoints;
            }
            if (requirementTotal > points) return `Câu ${index + 1}: tổng điểm yêu cầu con không được vượt điểm câu hỏi.`;
            total += points;
        }
        return total > maximum ? 'Tổng điểm câu hỏi tự luận không được vượt điểm tối đa của bài tập.' : '';
    }
    function updatePreviewSummary() {
        const list = previews[authoringTab];
        if (!list) return;
        const total = list.reduce((sum, task) => sum + (pointsInCents(task.maximum_points) || 0), 0);
        byId('preview-summary').textContent = `${list.length} câu hỏi · Tổng điểm: ${formatPoints(total)} / ${byId('maximumScore').value || '—'}`;
        byId('preview-editor').querySelectorAll('.requirement-summary').forEach((summary, index) => {
            const task = list[index];
            const requirementTotal = task.requirements.reduce((sum, requirement) => sum + (pointsInCents(requirement.maximum_points) || 0), 0);
            summary.textContent = `${task.requirements.length} / ${requirementLimit} yêu cầu con · Tổng điểm: ${formatPoints(requirementTotal)} / ${task.maximum_points || '—'}`;
            summary.classList.toggle('over-limit', pointsInCents(task.maximum_points) !== null && requirementTotal > pointsInCents(task.maximum_points));
        });
    }
    function previewChanged() { dirty = true; byId('preview-error').textContent = ''; updatePreviewSummary(); }
    function renderPreview() {
        const list = previews[authoringTab];
        byId('authoring-preview').hidden = !list;
        byId('preview-error').textContent = '';
        byId('preview-editor').replaceChildren();
        if (list) { renderEssayEditor(byId('preview-editor'), list, true); updatePreviewSummary(); }
    }
    const authoringTabs = Array.from(form.querySelectorAll('[data-essay-tab]'));
    function selectAuthoringTab(value, focus = false) {
        authoringTab = value;
        authoringTabs.forEach(tab => {
            const selected = tab.dataset.essayTab === value;
            tab.setAttribute('aria-selected', String(selected)); tab.tabIndex = selected ? 0 : -1;
            byId(`essay-panel-${tab.dataset.essayTab}`).hidden = !selected;
            if (selected && focus) tab.focus();
        });
        renderPreview();
    }
    authoringTabs.forEach((tab, index) => {
        tab.addEventListener('click', () => selectAuthoringTab(tab.dataset.essayTab));
        tab.addEventListener('keydown', event => {
            let next = index;
            if (event.key === 'ArrowRight') next = (index + 1) % authoringTabs.length;
            else if (event.key === 'ArrowLeft') next = (index + authoringTabs.length - 1) % authoringTabs.length;
            else if (event.key === 'Home') next = 0;
            else if (event.key === 'End') next = authoringTabs.length - 1;
            else return;
            event.preventDefault(); selectAuthoringTab(authoringTabs[next].dataset.essayTab, true);
        });
    });
    function showAuthoringStatus(source, message, error = false) {
        const status = byId(`${source}-status`);
        status.textContent = message; status.classList.toggle('field-error', error);
    }
    function authoringFiles(source) {
        const files = Array.from(byId(`${source}-files`).files || []);
        if (source === 'import' && files.length === 0) throw new Error('Chọn ít nhất một file để import.');
        if (files.length > 3) throw new Error('Chỉ chọn tối đa 3 file mỗi lần.');
        for (const file of files) {
            if (!/\.(txt|docx|pdf|xlsx|xls)$/i.test(file.name)) throw new Error('Chỉ hỗ trợ file .txt, .docx, .pdf, .xlsx và .xls.');
            if (file.size > 2 * 1024 * 1024) throw new Error(`File “${file.name}” vượt quá 2 MB.`);
            if (file.size === 0) throw new Error(`File “${file.name}” không có nội dung.`);
        }
        return files;
    }
    async function requestAuthoring(source) {
        if (requests[source]) return;
        let files, payload;
        try {
            files = authoringFiles(source);
            payload = new FormData();
            payload.set('action', source);
            payload.set('classId', form.querySelector('[name="classId"]').value);
            payload.set('csrf', form.querySelector('[name="csrf"]').value);
            const maximum = byId('maximumScore').value;
            if (!/^[0-9]+$/.test(maximum) || Number(maximum) < 1 || Number(maximum) > 100)
                throw new Error('Nhập điểm tối đa của bài tập là số nguyên từ 1 đến 100 trước khi soạn câu hỏi.');
            payload.set('maximumScore', maximum);
            files.forEach(file => payload.append('files', file));
            if (source === 'generate') {
                const prompt = byId('generate-prompt').value.trim();
                if (!prompt) throw new Error('Nhập yêu cầu để AI soạn câu hỏi.');
                const context = byId('generate-context').value.trim();
                if (prompt.length > 4000) throw new Error('Yêu cầu soạn đề không được vượt 4.000 ký tự.');
                if (context.length > 12000) throw new Error('Ngữ cảnh không được vượt 12.000 ký tự.');
                for (const [id, name, limit] of [['generate-question-count', 'questionCount', taskLimit], ['generate-requirement-count', 'requirementCount', requirementLimit]]) {
                    const value = byId(id).value;
                    if (!/^[0-9]+$/.test(value) || Number(value) < 1 || Number(value) > limit)
                        throw new Error(`${name === 'questionCount' ? 'Số câu hỏi' : 'Số yêu cầu con'} phải là số nguyên từ 1 đến ${limit}.`);
                    payload.set(name, value);
                }
                payload.set('prompt', prompt); payload.set('context', context);
            }
        } catch (error) { showAuthoringStatus(source, error.message, true); return; }
        requests[source] = true;
        const fields = byId(`${source}-source-fields`);
        fields.disabled = true; fields.setAttribute('aria-busy', 'true');
        showAuthoringStatus(source, source === 'generate' ? 'AI đang soạn câu hỏi…' : 'Đang đọc file…');
        const controller = new AbortController();
        const timeout = window.setTimeout(() => controller.abort(), 90000);
        try {
            const response = await fetch(form.dataset.authoringUrl, {method: 'POST', body: payload,
                credentials: 'same-origin', headers: {'Accept': 'application/json'}, signal: controller.signal});
            const result = await response.json().catch(() => null);
            if (!response.ok) throw new Error(result?.error || (response.status === 503
                ? 'AI hiện chưa được cấu hình hoặc tạm thời không khả dụng. Bạn vẫn có thể nhập thủ công hoặc import từ file.'
                : 'Không thể xử lý yêu cầu. Vui lòng thử lại.'));
            if (!result || !Array.isArray(result.tasks)) throw new Error('Máy chủ trả về nội dung không hợp lệ. Vui lòng thử lại.');
            let received;
            try { received = normalizeTasks(result.tasks); }
            catch (_) { throw new Error('Kết quả có cấu trúc câu hỏi hoặc yêu cầu con không hợp lệ. Vui lòng thử lại.'); }
            if (!received.length) throw new Error('Không tìm thấy câu hỏi trong kết quả. Kiểm tra nội dung và thử lại.');
            previews[source] = received; dirty = true;
            showAuthoringStatus(source, result.message || 'Câu hỏi đã sẵn sàng để xem trước.');
            if (authoringTab === source) { renderPreview(); byId('preview-heading').focus(); }
        } catch (error) {
            showAuthoringStatus(source, error.name === 'AbortError' ? 'Xử lý quá thời gian chờ. Vui lòng thử lại.'
                : error instanceof TypeError ? 'Không thể kết nối máy chủ. Kiểm tra kết nối và thử lại.' : error.message, true);
        } finally {
            window.clearTimeout(timeout); requests[source] = false;
            fields.disabled = false; fields.setAttribute('aria-busy', 'false');
        }
    }
    function emptyTask(task) {
        if (task.title.trim() || task.description.trim()) return false;
        return task.requirements.every(requirement => !requirement.title.trim()
            || (task.requirements.length === 1 && requirement.title.trim() === 'Trả lời câu hỏi'
                && requirement.answer_type === 'TEXT'));
    }
    function insertPreview(preview) {
        let next = 0;
        const combined = [];
        for (const task of tasks) {
            if (!emptyTask(task)) combined.push(task);
            else if (next < preview.length) combined.push(preview[next++]);
        }
        return [...combined, ...preview.slice(next)];
    }
    function applyPreview(replace) {
        const preview = previews[authoringTab];
        if (!preview) return;
        const existing = tasks.filter(task => !emptyTask(task));
        let error = essayError(preview);
        if (!error && !replace && existing.length + preview.length > taskLimit) error = `Đề chỉ được có tối đa ${taskLimit} câu hỏi. Hãy giảm số câu hoặc chọn thay thế.`;
        if (!error && !replace) {
            const total = [...existing, ...preview].reduce((sum, task) => sum + (pointsInCents(task.maximum_points) || 0), 0);
            if (total > pointsInCents(byId('maximumScore').value)) error = 'Thêm các câu này sẽ vượt điểm tối đa của bài tập. Hãy điều chỉnh điểm hoặc chọn thay thế.';
        }
        if (error) { byId('preview-error').textContent = error; byId('preview-error').focus(); return; }
        if (replace && existing.length && !confirm(`Thay thế toàn bộ ${existing.length} câu hỏi tự luận hiện tại bằng ${preview.length} câu hỏi trong phần xem trước?`)) return;
        tasks = replace ? normalizeTasks(preview) : insertPreview(normalizeTasks(preview));
        previews[authoringTab] = null;
        changed(); renderTasks(); renderPreview();
        byId('applied-status').textContent = `Đã ${replace ? 'thay thế bằng' : 'thêm'} ${preview.length} câu hỏi vào đề. Chọn “${saveLabel}” ở cuối trang để lưu.`;
        byId('applied-heading').focus();
    }
    byId('import-questions').addEventListener('click', () => requestAuthoring('import'));
    byId('generate-questions').addEventListener('click', () => requestAuthoring('generate'));
    byId('append-preview').addEventListener('click', () => applyPreview(false));
    byId('replace-preview').addEventListener('click', () => applyPreview(true));
    byId('discard-preview').addEventListener('click', () => {
        if (confirm('Bỏ kết quả xem trước này?')) { previews[authoringTab] = null; renderPreview(); }
    });
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
    form.addEventListener('input', () => { changed(); updatePreviewSummary(); });
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
        if (responseType === 'OBJECTIVE' && total !== pointsInCents(byId('maximumScore').value)) {
            reject('Tổng điểm các câu hỏi phải bằng điểm tối đa của bài tập.'); return;
        }
        if (responseType === 'ESSAY') {
            const error = essayError(tasks);
            if (error) { reject(error); return; }
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
