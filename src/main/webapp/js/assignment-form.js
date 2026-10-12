(() => {
    'use strict';
    const form = document.getElementById('assignment-form');
    if (!form) return;
    const byId = id => document.getElementById(id);
    const editing = form.dataset.edit === 'true';
    const saveLabel = editing ? 'Save Changes' : 'Save Draft';
    const saveButton = byId('save-draft');
    const templates = JSON.parse(byId('template-data').value);
    let tasks, questions;
    try {
        tasks = JSON.parse(byId('tasks-data').value || '[]');
        questions = JSON.parse(byId('questions-data').value || '[]');
        if (!Array.isArray(tasks) || !Array.isArray(questions)) throw new Error('Invalid content');
        if (tasks.some(t => !t || typeof t.title !== 'string' || (t.description != null && typeof t.description !== 'string'))
                || questions.some(q => !q || typeof q.question_text !== 'string' || typeof q.maximum_points !== 'string'
                    || !Array.isArray(q.options) || q.options.some(o => !o || typeof o.option_text !== 'string'
                        || typeof o.is_correct !== 'boolean'))) throw new Error('Invalid content');
    } catch (_) {
        // Keep an invalid submitted payload intact for diagnosis; never silently erase it.
        byId('tasksJson-error').textContent = `The submitted content could not be loaded. Return to the list and reopen ${editing ? 'Edit' : 'Create'}.`;
        return;
    }
    const savedResponseType = form.dataset.savedResponseType || byId('responseType').value;
    const savedEssayContent = form.dataset.savedEssayContent === ''
        ? Boolean(byId('scopeText').value.trim() || tasks.length)
        : form.dataset.savedEssayContent === 'true';
    const savedObjectiveContent = form.dataset.savedObjectiveContent === ''
        ? questions.length > 0
        : form.dataset.savedObjectiveContent === 'true';
    let dirty = Boolean(document.querySelector('.notice.error'));
    function sync() {
        // Retain both editors after validation errors; the server saves only the active type.
        questions.forEach(question => {
            if (question.question_id === undefined) question.question_id = 0;
            question.options.forEach((option, index) => { option.option_order = index + 1; });
        });
        byId('tasks-data').value = JSON.stringify(tasks);
        byId('questions-data').value = JSON.stringify(questions);
        const total = questions.reduce((sum, q) => sum + (Number(q.maximum_points) || 0), 0);
        byId('points-total').textContent = `Question total: ${total.toFixed(2)} / ${byId('maximumScore').value || '—'} points`;
    }
    function changed() { dirty = true; sync(); }
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
        const input = node(kind === 'number' ? 'input' : kind);
        if (kind === 'number') { input.type = 'number'; input.min = '0.01'; input.max = '99999999.99'; input.step = '0.01'; }
        if (kind === 'textarea') input.rows = 2;
        input.value = value || ''; input.required = required;
        input.addEventListener('input', () => { update(input.value); changed(); });
        wrap.append(input); return wrap;
    }
    function controls(list, index, render, label) {
        const actions = node('div', undefined, 'editor-actions');
        function move(offset) { [list[index], list[index + offset]] = [list[index + offset], list[index]]; changed(); render(); }
        actions.append(button('Move up', () => move(-1), index === 0),
            button('Move down', () => move(1), index === list.length - 1),
            button('Remove', () => { if (confirm(`Remove this ${label} from the draft?`)) { list.splice(index, 1); changed(); render(); } }));
        return actions;
    }
    function renderTasks() {
        const editor = byId('task-editor'); editor.replaceChildren();
        if (!tasks.length) editor.append(node('p', 'No tasks added yet.', 'form-hint'));
        tasks.forEach((task, index) => {
            const card = node('article', undefined, 'editor-card');
            const heading = node('div', undefined, 'editor-heading');
            heading.append(node('strong', `Task ${index + 1}`), controls(tasks, index, renderTasks, 'task'));
            card.append(heading, field('Task title *', task.title, value => task.title = value, 'input', true),
                field('Description', task.description, value => task.description = value));
            editor.append(card);
        });
    }
    function renderQuestions() {
        const editor = byId('question-editor'); editor.replaceChildren();
        if (!questions.length) editor.append(node('p', 'No questions added yet. You can add them before publication.', 'form-hint'));
        questions.forEach((question, index) => {
            const card = node('article', undefined, 'editor-card');
            const heading = node('div', undefined, 'editor-heading');
            heading.append(node('strong', `Question ${index + 1}`), controls(questions, index, renderQuestions, 'question'));
            card.append(heading, field('Question text *', question.question_text, value => question.question_text = value, 'textarea', true),
                field('Question points *', question.maximum_points, value => question.maximum_points = value, 'number', true));
            question.options.forEach((option, optionIndex) => {
                const row = node('div', undefined, 'option-row');
                const correct = node('label', undefined, 'correct-choice');
                const radio = node('input'); radio.type = 'radio'; radio.name = `correct-${index}`;
                radio.checked = option.is_correct === true;
                radio.addEventListener('change', () => { question.options.forEach(o => o.is_correct = o === option); changed(); });
                correct.append(radio, node('span', 'Correct'));
                row.append(correct, field(`Option ${optionIndex + 1} *`, option.option_text, value => option.option_text = value, 'input', true),
                    controls(question.options, optionIndex, renderQuestions, 'option'));
                card.append(row);
            });
            const actions = node('div', undefined, 'editor-actions');
            actions.append(button('Add option', () => { question.options.push({option_text: '', is_correct: false}); changed(); renderQuestions(); }),
                button('Clear correct answer', () => { question.options.forEach(o => o.is_correct = false); changed(); renderQuestions(); }));
            card.append(actions); editor.append(card);
        });
    }
    let previousTemplate = byId('templateId').value;
    byId('templateId').addEventListener('change', () => {
        const select = byId('templateId');
        const template = templates.find(t => String(t.templateId) === select.value);
        if (!template) return;
        const hasContent = ['title', 'instructions', 'maximumScore'].some(id => byId(id).value.trim());
        if (hasContent && !confirm('Replace the title, instructions and maximum score with this template? Other content will be kept.')) {
            select.value = previousTemplate; return;
        }
        byId('title').value = template.title;
        byId('instructions').value = template.instructions || '';
        byId('maximumScore').value = template.maximumScore;
        previousTemplate = select.value; changed();
    });
    function updateType() {
        const practice = byId('purpose').value === 'PRACTICE';
        if (practice) byId('responseType').value = 'OBJECTIVE';
        byId('responseType').querySelector('option[value="ESSAY"]').disabled = practice;
        byId('attempt-field').hidden = !practice;
        byId('attemptLimit').disabled = !practice;
        const objective = byId('responseType').value === 'OBJECTIVE';
        byId('essay-section').hidden = objective;
        byId('essay-fields').disabled = objective;
        byId('question-section').hidden = !objective;
        byId('question-fields').disabled = !objective;
    }
    byId('purpose').addEventListener('change', () => { updateType(); changed(); });
    byId('responseType').addEventListener('change', () => {
        updateType(); changed();
    });
    byId('add-task').addEventListener('click', () => { tasks.push({title: '', description: ''}); changed(); renderTasks(); });
    byId('add-question').addEventListener('click', () => {
        questions.push({question_id: 0, question_text: '', maximum_points: '', options: [
            {option_text: '', is_correct: false}, {option_text: '', is_correct: false}]});
        changed(); renderQuestions();
    });
    form.addEventListener('input', changed);
    form.addEventListener('change', () => { dirty = true; });
    window.addEventListener('beforeunload', event => { if (dirty) { event.preventDefault(); event.returnValue = ''; } });
    document.querySelectorAll('.leave-form').forEach(link => link.addEventListener('click', event => {
        if (dirty && !confirm('Leave this form and discard unsaved changes?')) event.preventDefault();
        else dirty = false;
    }));
    form.addEventListener('submit', event => {
        if (saveButton.dataset.canSave !== 'true' || form.dataset.editConflict === 'true') {
            event.preventDefault(); return;
        }
        sync();
        const responseType = byId('responseType').value;
        const removingSavedContent = editing && responseType !== savedResponseType
            && ((savedResponseType === 'ESSAY' && savedEssayContent)
                || (savedResponseType === 'OBJECTIVE' && savedObjectiveContent));
        if (removingSavedContent) {
            const removedContent = savedResponseType === 'ESSAY' ? 'essay scope and tasks' : 'objective questions and options';
            const selectedType = responseType === 'OBJECTIVE' ? 'Objective' : 'Essay';
            if (!confirm(`Save as ${selectedType}? This will remove the ${removedContent} currently saved in this draft.`)) {
                event.preventDefault(); return;
            }
        }
        dirty = false;
        saveButton.disabled = true;
        saveButton.textContent = 'Saving…';
    });
    renderTasks(); renderQuestions(); updateType(); sync();
    saveButton.disabled = saveButton.dataset.canSave !== 'true';
    window.addEventListener('pageshow', () => {
        saveButton.disabled = saveButton.dataset.canSave !== 'true';
        saveButton.textContent = saveLabel;
    });
    byId('form-errors')?.focus();
})();
