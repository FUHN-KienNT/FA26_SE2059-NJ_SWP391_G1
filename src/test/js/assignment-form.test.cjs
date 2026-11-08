const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const code = fs.readFileSync(path.join(__dirname, '../../main/webapp/js/assignment-form.js'), 'utf8');

function harness({
    tasks = [],
    questions = [],
    responseType = 'ESSAY',
    maximum = '10',
    templates = [],
    instructions = '',
    fetchResult,
    status = 200
} = {}) {
    const ids = new Map(),
        windowEvents = {},
        calls = [],
        confirmations = [];

    class Element {
        constructor(tag = 'div') {
            this.tagName = tag;
            this.children = [];
            this.dataset = {};
            this.events = {};
            this.attributes = {};
            this.value = '';
            this._text = '';
            this.className = '';
            this.disabled = false;
            this.required = false;
            this.validity = {};
            this.customError = '';
            this.files = [];
            this.classList = {
                toggle: (name, on) => {
                    const classes = new Set(this.className.split(' ').filter(Boolean));

                    if (on) classes.add(name);
                    else classes.delete(name);

                    this.className = [...classes].join(' ');
                }
            };
        }
        set id(value) {
            this._id = value;
            ids.set(value, this);
        }
        get id() {
            return this._id;
        }
        set textContent(value) {
            this._text = value;
            this.children = [];
        }
        get textContent() {
            return this._text + this.children.map(child => child.textContent).join('');
        }
        append(...children) {
            children.forEach(child => {
                child.parent = this;
                this.children.push(child);
            });
        }
        replaceChildren(...children) {
            this.children = [];
            this.append(...children);
        }
        addEventListener(name, handler) {
            (this.events[name] ||= []).push(handler);
        }
        async fire(name, extra = {}) {
            const event = {
                target: this,
                prevented: false,
                preventDefault() {
                    this.prevented = true;
                },
                ...extra
            };
            await Promise.all((this.events[name] || []).map(handler => handler(event)));
            return event;
        }
        setAttribute(name, value) {
            this.attributes[name] = value;
        }
        focus() {
            this.focused = true;
        }
        setCustomValidity(value) {
            this.customError = value;
            this.validity.customError = Boolean(value);
        }
        get willValidate() {
            if (!['input', 'textarea', 'select'].includes(this.tagName) || this.hidden || this.type === 'hidden')
        return false;

            for (let ancestor = this; ancestor; ancestor = ancestor.parent)
                if (ancestor.disabled) return false;

            return true;
        }
        querySelectorAll(selector) {
            const matches = element => selector.split(',').some(part => {
                part = part.trim();

                if (part.startsWith('.')) return element.className.split(' ').includes(part.slice(1));

                const data = part.match(/^\[data-([a-z-]+)\]$/);

                if (data) return data[1].replace(/-([a-z])/g, (_, letter) => letter.toUpperCase()) in element.dataset;

                const attribute = part.match(/^(\w+)?\[([\w-]+)="([^"]+)"\]$/);

                if (attribute) return (!attribute[1] || element.tagName === attribute[1]) && String(element[attribute[
                    2]]) === attribute[3];

                return element.tagName === part;
            });
            const result = [];
            const visit = element => element.children.forEach(child => {
                if (matches(child)) result.push(child);

                visit(child);
            });
            visit(this);
            return result;
        }
        querySelector(selector) {
            return this.querySelectorAll(selector)[0] || null;
        }
    }

    function element(id, tag = 'div', parent) {
        const result = new Element(tag);
        result.id = id;

        if (parent) parent.append(result);

        return result;
    }

    const form = element('assignment-form', 'form');
    form.dataset = {
        edit: 'false',
        authoringUrl: '/app/teacher/assignment-authoring',
        savedEssayContent: '',
        savedObjectiveContent: ''
    };
    form.reportValidity = () => form.querySelectorAll('input,select,textarea').every(input => !input.willValidate ||
        (!input.customError && (!input.required || input.value.trim()) && (!input.pattern || !input.value || new RegExp(
            `^(?:${input.pattern})$`).test(input.value))));
    const values = {
        'template-data': JSON.stringify(templates),
        'tasks-data': JSON.stringify(tasks),
        'questions-data': JSON.stringify(questions),
        maximumScore: maximum,
        responseType,
        purpose: 'REGULAR',
        title: 'Bài tập',
        templateId: '1',
        instructions,
        'submit-mode': 'draft',
        attemptLimit: ''
    };

    Object.entries(values).forEach(([id, value]) => {
        element(id, id.endsWith('-data') ? 'textarea' : 'input', form).value = value;
    });
    element('publish-assignment', 'button', form);

    ids.get('maximumScore').dataset = {
        integerMin: '1',
        integerMax: '100'
    };

    for (const value of ['ESSAY', 'OBJECTIVE']) {
        const option = new Element('option');
        option.value = value;
        ids.get('responseType').append(option);
    }

    ['classId', 'csrf'].forEach(name => {
        const input = new Element('input');
        input.name = name;
        input.value = `${name}-value`;
        form.append(input);
    });

    ['attempt-field', 'tasksJson-error', 'questionsJson-error', 'client-form-error', 'essay-section', 'question-section',
        'task-count', 'question-count', 'points-total', 'task-points-total', 'points-auto-fill'
    ].forEach(id => element(id, 'div', form));
    const essay = element('essay-fields', 'fieldset', form),
        objective = element('question-fields', 'fieldset', form);
    element('question-editor', 'div', objective);
    element('add-question', 'button', objective);

    for (const source of ['manual', 'import', 'generate']) {
        const tab = element(`essay-tab-${source}`, 'button', essay);
        tab.dataset.essayTab = source;
        element(`essay-panel-${source}`, 'div', essay);
    }

    const applied = element('applied-questions', 'section', essay);
    element('applied-heading', 'h3', applied);
    element('applied-status', 'p', applied);
    element('task-editor', 'div', applied);
    element('add-task', 'button', applied);

    for (const source of ['import', 'generate']) {
        const fields = element(`${source}-source-fields`, 'fieldset', ids.get(`essay-panel-${source}`));
        element(`${source}-files`, 'input', fields);
        element(`${source}-questions`, 'button', fields);
        element(`${source}-status`, 'p', ids.get(`essay-panel-${source}`));
    }

    for (const [id, value] of [
            ['generate-prompt', 'Soạn câu hỏi'],
            ['generate-context', 'Ngữ cảnh'],
            ['generate-question-count', '2'],
            ['generate-requirement-count', '2']
        ])
        element(id, 'input', ids.get('generate-source-fields')).value = value;

    const preview = element('authoring-preview', 'section', essay);
    ['preview-heading', 'preview-summary', 'preview-editor', 'preview-error'].forEach(id => element(id, 'div', preview));
    ['append-preview', 'replace-preview', 'discard-preview'].forEach(id => element(id, 'button', preview));
    const save = element('save-draft', 'button', form);
    save.dataset.canSave = 'true';
    const document = {
        getElementById: id => ids.get(id) || null,
        createElement: tag => new Element(tag),
        querySelector: () => null,
        querySelectorAll: () => []
    };
    const context = {
        document,
        Object,
        TypeError,
        FormData,
        AbortController,
        confirm: message => {
            confirmations.push(message);
            return context.acceptConfirmation;
        },
        acceptConfirmation: true,
        fetch: async (url, options) => {
            calls.push({
                url,
                options
            });
            return {
                ok: status >= 200 && status < 300,
                status,
                json: async () => fetchResult
            };
        },
        window: {
            addEventListener: (name, handler) => {
                windowEvents[name] = handler;
            },
            setTimeout: () => 1,
            clearTimeout: () => {}
        }
    };
    vm.runInNewContext(code, context);
    return {
        ids,
        form,
        calls,
        confirmations,
        context,
        windowEvents,
        data: () => JSON.parse(ids.get('tasks-data').value),
        questions: () => JSON.parse(ids.get('questions-data').value),
        click: id => ids.get(id).fire('click'),
        submit: () => form.fire('submit'),
        button: (parent, label) => parent.querySelectorAll('button').find(button => button.textContent === label)
    };
}

const requirement = (points = '2', answer_type = 'TEXT') => ({
    title: 'Phân tích yêu cầu',
    maximum_points: points,
    answer_type
});
const task = (points = '5', requirements = [requirement()]) => ({
    title: 'Phân tích hệ thống',
    maximum_points: points,
    requirements
});

test('rubrics and selected drawing tools survive form serialization and explicit tool changes', async () => {
    const h = harness({tasks: [{
        ...task('5', [{...requirement('5', 'DIAGRAM'), rubric: 'Đúng ký hiệu', allowed_tools: ['PLANTUML']}]),
        rubric: 'Đánh giá cấu trúc'
    }]});
    assert.equal(h.data()[0].rubric, 'Đánh giá cấu trúc');
    assert.equal(h.data()[0].requirements[0].rubric, 'Đúng ký hiệu');
    assert.deepEqual(h.data()[0].requirements[0].allowed_tools, ['PLANTUML']);
    const toolGroup = h.ids.get('task-editor').querySelector('.drawing-tools');
    assert.equal(toolGroup.querySelector('summary'), null);
    assert.match(toolGroup.querySelector('.selected-drawing-tools').textContent, /Công cụ được chọn: PlantUML/);
    const tools = h.ids.get('task-editor').querySelectorAll('input').filter(input => input.type === 'checkbox');
    assert.equal(tools.length, 2);
    tools[0].checked = true;
    await tools[0].fire('change');
    assert.deepEqual(h.data()[0].requirements[0].allowed_tools, ['PLANTUML', 'DRAWIO']);
    assert.match(toolGroup.querySelector('.selected-drawing-tools').textContent, /PlantUML, draw\.io/);
    tools[0].checked = false;
    await tools[0].fire('change');
    tools[1].checked = false;
    await tools[1].fire('change');
    assert.equal(toolGroup.querySelector('.selected-drawing-tools').textContent, 'Chưa chọn công cụ vẽ');
    assert.equal((await h.submit()).prevented, true);
    assert.match(h.ids.get('client-form-error').textContent, /chọn ít nhất một công cụ vẽ/i);
});

test('selecting a template changes visible defaults without injecting or overwriting hidden instructions', async () => {
    for (const legacy of ['', 'Hướng dẫn cũ dòng một.\nHướng dẫn cũ dòng hai.']) {
        const h = harness({
            instructions: legacy,
            templates: [{templateId: 2, title: 'Tình huống từ mẫu', instructions: 'Không tự chép hướng dẫn này.', maximumScore: 20}]
        });
        h.ids.get('templateId').value = '2';
        await h.ids.get('templateId').fire('change');
        assert.equal(h.ids.get('title').value, 'Tình huống từ mẫu');
        assert.equal(h.ids.get('maximumScore').value, '20');
        assert.equal(h.ids.get('instructions').value, legacy);
    }
});

test('publish submitter sends the explicit publish mode', async () => {
    const h = harness({tasks: [task()]});
    const event = await h.form.fire('submit', {submitter: h.ids.get('publish-assignment')});
    assert.equal(event.prevented, false);
    assert.equal(h.ids.get('submit-mode').value, 'publish');
    assert.equal(h.ids.get('publish-assignment').disabled, true);
});

test('legacy essay content becomes one ordered TEXT requirement without losing description', async () => {
    const h = harness({
        tasks: [{
            title: 'Đề cũ',
            description: 'Chi tiết',
            maximum_points: 5
        }]
    });
    assert.deepEqual(h.data(), [{
        task_order: 1,
        title: 'Đề cũ\nChi tiết',
        description: '',
        rubric: '',
        maximum_points: '5',
        requirements: [{
            requirement_order: 1,
            title: 'Trả lời câu hỏi',
            maximum_points: '5',
            answer_type: 'TEXT',
            rubric: '',
            allowed_tools: []
        }]
    }]);
    assert.equal((await h.submit()).prevented, false);
});

test('essay allows unused question and assignment points, rejects child sums and overall overflow', async () => {
    assert.equal((await harness({
        tasks: [task('5', [requirement('1.25'), requirement('2.50', 'BOTH')])]
    }).submit()).prevented, false);
    const childOverflow = harness({
        tasks: [task('3', [requirement('2'), requirement('2', 'DIAGRAM')])]
    });
    assert.equal((await childOverflow.submit()).prevented, true);
    assert.match(childOverflow.ids.get('client-form-error').textContent, /tổng điểm yêu cầu con/i);
    const overall = harness({
        tasks: [task('6'), task('5')]
    });
    assert.equal((await overall.submit()).prevented, true);
    assert.match(overall.ids.get('client-form-error').textContent, /không được vượt/i);
});

test('requirement controls cap at ten, preserve answer type and recalculate ordering after removal', async () => {
    const h = harness({
        tasks: [task('10', Array.from({
            length: 10
        }, (_, i) => ({
            ...requirement('0.01', i === 9 ? 'BOTH' : 'TEXT'),
            title: `R${i}`
        })))]
    });
    const editor = h.ids.get('task-editor');
    assert.equal(h.button(editor, 'Thêm yêu cầu con').disabled, true);
    const requirementCards = editor.querySelectorAll('.requirement-card');
    await h.button(requirementCards[9], 'Chuyển lên').fire('click');
    assert.equal(h.data()[0].requirements[8].answer_type, 'BOTH');
    await h.button(editor.querySelectorAll('.requirement-card')[8], 'Xóa').fire('click');
    assert.equal(h.data()[0].requirements.length, 9);
    assert.deepEqual(h.data()[0].requirements.map(item => item.requirement_order), [1, 2, 3, 4, 5, 6, 7, 8, 9]);
    assert.equal(h.button(editor, 'Thêm yêu cầu con').disabled, false);
});

test('Objective retains one-blank autofill and exact-total validation', async () => {
    const question = points => ({
        question_text: 'Chọn đáp án',
        maximum_points: points,
        options: []
    });
    const h = harness({
        responseType: 'OBJECTIVE',
        questions: [question('4.25'), question('')]
    });
    assert.equal((await h.submit()).prevented, false);
    assert.equal(h.questions()[1].maximum_points, '5.75');
    const mismatch = harness({
        responseType: 'OBJECTIVE',
        questions: [question('9')]
    });
    assert.equal((await mismatch.submit()).prevented, true);
    assert.match(mismatch.ids.get('client-form-error').textContent, /phải bằng/);
});

test('import validates files before upload and sends CSRF only after explicit request', async () => {
    const h = harness({
        tasks: [task()],
        fetchResult: {
            tasks: [task('4')]
        }
    });
    await h.click('essay-tab-import');
    assert.equal(h.calls.length, 0);
    h.ids.get('import-files').files = [new File(['text'], 'test.exe')];
    await h.click('import-questions');
    assert.equal(h.calls.length, 0);
    assert.match(h.ids.get('import-status').textContent, /Chỉ hỗ trợ/);
    h.ids.get('import-files').files = [new File(['text'], 'test.txt')];
    await h.click('import-questions');
    assert.equal(h.calls.length, 1);
    assert.equal(h.calls[0].options.body.get('csrf'), 'csrf-value');
    assert.equal(h.calls[0].options.body.get('action'), 'import');
    assert.equal(h.data().length, 1);
    assert.equal(h.data()[0].maximum_points, '5');
    assert.equal(h.ids.get('authoring-preview').hidden, false);
    await h.click('essay-tab-manual');
    await h.click('essay-tab-import');
    assert.equal(h.ids.get('authoring-preview').hidden, false);
    h.context.acceptConfirmation = false;
    await h.click('append-preview');
    assert.equal(h.data().length, 2);
    assert.equal(h.confirmations.length, 0);
    assert.equal(h.ids.get('essay-panel-import').hidden, false);
    assert.equal((await h.submit()).prevented, false);
    assert.equal(h.ids.get('essay-panel-import').hidden, false);
});

test('generated preview is editable and replacement needs explicit confirmation', async () => {
    const h = harness({
        tasks: [task()],
        fetchResult: {
            tasks: [task('7')]
        }
    });
    await h.click('essay-tab-generate');
    await h.click('generate-questions');
    const payload = h.calls[0].options.body;
    assert.equal(payload.get('context'), 'Ngữ cảnh');
    assert.equal(payload.get('questionCount'), '2');
    const input = h.ids.get('preview-editor').querySelector('textarea');
    input.value = 'Đề đã sửa';
    await input.fire('input');
    await h.click('essay-tab-import');
    await h.click('essay-tab-generate');
    assert.equal(h.ids.get('preview-editor').querySelector('textarea').value, 'Đề đã sửa');
    assert.equal(h.data()[0].title, 'Phân tích hệ thống');
    await h.click('replace-preview');
    assert.equal(h.data()[0].title, 'Đề đã sửa');
    assert.equal(h.data()[0].maximum_points, '7');
    assert.match(h.confirmations[0], /Thay thế toàn bộ/);
});

test('Excel xlsx and xls files can be uploaded for preview and added to the assignment', async () => {
    for (const extension of ['xlsx', 'XLS']) {
        const h = harness({
            fetchResult: {
                tasks: [task('10')]
            }
        });
        await h.click('essay-tab-import');
        h.ids.get('import-files').files = [new File(['excel-fixture'], `questions.${extension}`)];
        await h.click('import-questions');
        assert.equal(h.calls.length, 1);
        assert.equal(h.calls[0].options.body.get('files').name, `questions.${extension}`);
        assert.equal(h.ids.get('authoring-preview').hidden, false);
        await h.click('append-preview');
        assert.equal(h.data().length, 1);
        assert.equal((await h.submit()).prevented, false);
    }
});

test('AI append applies once, removes the untouched initial question and saves without switching to manual', async () => {
    const h = harness({
        fetchResult: {
            tasks: [{
                ...task('10'),
                title: 'Câu hỏi do AI soạn'
            }]
        }
    });
    await h.click('essay-tab-generate');
    await h.click('generate-questions');
    // The append button itself is the confirmation, even when another dialog would be rejected.
    h.context.acceptConfirmation = false;
    await h.click('append-preview');
    assert.equal(h.confirmations.length, 0);
    assert.equal(h.data().length, 1);
    assert.equal(h.data()[0].title, 'Câu hỏi do AI soạn');
    assert.equal(h.ids.get('essay-panel-generate').hidden, false);
    assert.equal(h.ids.get('essay-panel-manual').hidden, true);
    assert.equal(h.ids.get('authoring-preview').hidden, true);
    await h.click('append-preview');
    assert.equal(h.data().length, 1);
    assert.equal((await h.submit()).prevented, false);
    assert.equal(h.ids.get('essay-panel-generate').hidden, false);
});

test('AI append preserves partially entered manual questions and cancelled replacement keeps both sources', async () => {
    const partial = {
        title: 'Câu đang viết dở',
        maximum_points: '',
        requirements: [{
            title: '',
            maximum_points: '',
            answer_type: 'TEXT'
        }]
    };
    const h = harness({
        tasks: [partial],
        fetchResult: {
            tasks: [task('5')]
        }
    });
    await h.click('essay-tab-generate');
    await h.click('generate-questions');
    await h.click('append-preview');
    assert.equal(h.data()[0].title, 'Câu đang viết dở');
    assert.equal(h.data().length, 2);
    assert.equal((await h.submit()).prevented, true);
    assert.equal(h.ids.get('essay-panel-generate').hidden, false);
    await h.click('generate-questions');
    const before = h.data();
    h.context.acceptConfirmation = false;
    await h.click('replace-preview');
    assert.deepEqual(h.data(), before);
    assert.equal(h.ids.get('authoring-preview').hidden, false);
});

test('AI fills the first blank question in place even when points and answer type were selected', async () => {
    const blank = {
        title: '   ',
        maximum_points: '5',
        requirements: [{
            title: '',
            maximum_points: '5',
            answer_type: 'DIAGRAM'
        }]
    };
    const manual = {
        ...task('5'),
        title: 'Câu thủ công đã viết'
    };
    const generated = {
        ...task('5'),
        title: 'Câu hỏi AI thay chỗ trống'
    };
    const h = harness({
        tasks: [blank, manual],
        fetchResult: {
            tasks: [generated]
        }
    });
    await h.click('essay-tab-generate');
    await h.click('generate-questions');
    await h.click('append-preview');
    assert.deepEqual(h.data().map(item => item.title), [generated.title, manual.title]);
    assert.deepEqual(h.data().map(item => item.task_order), [1, 2]);
    assert.equal((await h.submit()).prevented, false);
});

test('AI replaces a legacy blank question with its automatic response prompt', async () => {
    const h = harness({
        tasks: [{
            title: '',
            description: '',
            maximum_points: '10'
        }],
        fetchResult: {
            tasks: [task('10')]
        }
    });
    await h.click('essay-tab-generate');
    await h.click('generate-questions');
    await h.click('append-preview');
    assert.equal(h.data().length, 1);
    assert.equal(h.data()[0].title, 'Phân tích hệ thống');
    assert.equal((await h.submit()).prevented, false);
});

test('Gemini unavailable feedback preserves manual contents and restores request controls', async () => {
    const h = harness({
        tasks: [task()],
        status: 503
    });
    await h.click('essay-tab-generate');
    await h.click('generate-questions');
    assert.match(h.ids.get('generate-status').textContent, /chưa được cấu hình/);
    assert.equal(h.ids.get('generate-source-fields').disabled, false);
    assert.equal(h.data()[0].title, 'Phân tích hệ thống');
    assert.equal(h.ids.get('authoring-preview').hidden, true);
});
