const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const code = fs.readFileSync(path.join(__dirname, '../../main/webapp/js/student-essay.js'), 'utf8');

const answer = {
    task_order: 2, requirement_order: 3, text: 'Phân tích hệ thống.\nGiữ nguyên bài làm.',
    diagram_xml: '<mxfile><diagram name="Trang-1"><mxGraphModel/></diagram></mxfile>',
    diagram_plantuml: '',
    diagram_image: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a8WQAAAAASUVORK5CYII='
};
const settle = () => new Promise(resolve => setImmediate(resolve));
function response(status = 200, saved = {}) {
    return {status, ok: status >= 200 && status < 300, headers: {get: () => 'application/json'},
        json: async () => ({finished: false, serverTime: 100, deadline: 10_000, revision: 4, ...saved})};
}
function harness({expired = false, text = answer.text, diagram = answer, fetchResponse = () => response()} = {}) {
    class Element {
        constructor(tagName = 'DIV', value = '') {
            this.tagName = tagName; this.value = value; this.events = {}; this.disabled = false; this.readOnly = false;
            this.classList = {toggle() {}};
        }
        addEventListener(name, handler) { (this.events[name] ||= []).push(handler); }
        fire(name, extra = {}) {
            const event = {preventDefault() {}, ...extra};
            for (const handler of this.events[name] || []) handler(event);
        }
        append() {}
        replaceChildren() {}
        close() {}
    }
    const ids = new Map();
    const id = name => { if (!ids.has(name)) ids.set(name, new Element()); return ids.get(name); };
    const fields = {
        '.essay-text': new Element('TEXTAREA', text),
        '.diagram-xml': new Element('TEXTAREA', diagram.diagram_xml),
        '.diagram-plantuml': new Element('TEXTAREA', diagram.diagram_plantuml),
        '.diagram-image': new Element('TEXTAREA', diagram.diagram_image),
        '.open-diagram': new Element('BUTTON'), '.remove-diagram': new Element('BUTTON')
    };
    const row = {dataset: {task: '2', requirement: '3', answerType: 'BOTH'}, querySelector: selector => fields[selector] || null};
    const form = id('essay-work');
    form.dataset = {deadline: '10000', serverTime: expired ? '10000' : '100'};
    form.elements = Object.fromEntries(Object.entries({csrf: 'csrf-value', assignmentId: '11', submissionId: '22', answerRevision: '3'})
        .map(([name, value]) => [name, {value}]));
    form.getAttribute = () => '/app/student/assignments';
    const controls = [...Object.values(fields), id('essay-save')];
    controls.at(-1).tagName = 'BUTTON';
    form.querySelectorAll = selector => selector === '.essay-requirement' ? [row] : controls;
    id('essay-countdown').parentElement = new Element();
    const calls = [], redirects = [], timers = new Map(), intervals = [];
    let timerId = 0, elapsed = 0;
    const context = {
        document: {getElementById: id, createElement: () => new Element()},
        URL, URLSearchParams, TextEncoder, AbortController, performance: {now: () => elapsed},
        location: {href: 'https://ams.test/app/student/assignments', assign: url => redirects.push(url)},
        window: {addEventListener() {}}, confirm: () => true,
        setTimeout: (callback, delay) => { timers.set(++timerId, {callback, delay}); return timerId; },
        clearTimeout: handle => timers.delete(handle), setInterval: callback => intervals.push(callback),
        fetch: async (url, options) => {
            const data = Object.fromEntries(options.body);
            calls.push({url, data});
            return fetchResponse(data, calls.length);
        }
    };
    vm.runInNewContext(code, context);
    return {form, calls, redirects, fields, controls, status: id('essay-save-status'),
        submit: () => form.fire('submit'), advance: milliseconds => { elapsed += milliseconds; intervals.forEach(callback => callback()); }};
}
function submitted(h, expected = answer) {
    const call = h.calls.find(call => call.data.action === 'submitEssay');
    assert.ok(call, 'Expected a submission request');
    assert.deepEqual(JSON.parse(call.data.essayContent), {format: 'ams-essay-v2', answers: [expected]});
    return call.data;
}

test('client expiry submits keyed text and diagram content instead of an empty-answer sentinel', async () => {
    const h = harness({expired: true});
    await settle();
    assert.equal(submitted(h).answerRevision, '3');
    assert.equal(h.redirects.length, 1);
});

test('autosave and manual submission retain keyed text, PlantUML source and preview', async () => {
    const plantAnswer = {...answer, diagram_xml: '', diagram_plantuml: '@startuml\nSV -> AMS: Nộp bài\n@enduml'};
    const h = harness({diagram: plantAnswer});
    await settle();
    const saved = h.calls.find(call => call.data.action === 'saveEssay');
    assert.deepEqual(JSON.parse(saved.data.essayContent), {format: 'ams-essay-v2', answers: [plantAnswer]});
    h.submit();
    await settle();
    assert.equal(submitted(h, plantAnswer).answerRevision, '4');
    assert.equal(h.redirects.length, 1);
});

test('submission awaits the in-flight autosave and uses its updated revision', async () => {
    let saved;
    const pending = new Promise(resolve => { saved = resolve; });
    const h = harness({fetchResponse: data => data.action === 'saveEssay' ? pending : response()});
    h.submit();
    await settle();
    assert.deepEqual(h.calls.map(call => call.data.action), ['saveEssay']);
    assert.ok(h.controls.every(control => control.tagName === 'TEXTAREA' ? control.readOnly : control.disabled));
    saved(response(200, {revision: 8}));
    await settle();
    assert.equal(submitted(h).answerRevision, '8');
    assert.equal(h.redirects.length, 1);
});

test('manual HTTP 400 rejection unlocks existing work for correction and resubmission', async () => {
    let submissions = 0;
    const h = harness({fetchResponse: data => data.action === 'submitEssay' && ++submissions === 1 ? response(400) : response()});
    await settle();
    h.submit();
    await settle();
    submitted(h);
    assert.equal(h.redirects.length, 0);
    assert.ok(h.controls.every(control => control.tagName === 'TEXTAREA' ? !control.readOnly : !control.disabled));
    assert.equal(h.fields['.essay-text'].value, answer.text);
    assert.match(h.status.textContent, /Bài chưa được nộp/);
    h.submit();
    await settle();
    assert.equal(h.calls.filter(call => call.data.action === 'submitEssay').length, 2);
    assert.equal(h.redirects.length, 1);
});

test('oversized work remains editable before the deadline', async () => {
    const text = 'x'.repeat(1_900_000);
    const h = harness({text});
    h.submit();
    await settle();
    assert.equal(h.calls.length, 0);
    assert.equal(h.redirects.length, 0);
    assert.ok(h.controls.every(control => control.tagName === 'TEXTAREA' ? !control.readOnly : !control.disabled));
    assert.equal(h.fields['.essay-text'].value, text);
    assert.match(h.status.textContent, /vượt giới hạn dung lượng/);
});

test('oversized work at client expiry locks the editor and checks the result page without submitting blank answers', async () => {
    const h = harness({expired: true, text: 'x'.repeat(1_900_000)});
    await settle();
    assert.equal(h.calls.length, 0);
    assert.ok(h.controls.every(control => control.tagName === 'TEXTAREA' ? control.readOnly : control.disabled));
    assert.deepEqual(h.redirects, ['https://ams.test/app/student/assignments?assignmentId=11&submissionId=22']);
});

test('oversized expiry waits for the in-flight save before checking the result page', async () => {
    let saved;
    const pending = new Promise(resolve => { saved = resolve; });
    const h = harness({fetchResponse: () => pending});
    h.fields['.essay-text'].value = 'x'.repeat(1_900_000);
    h.advance(10_000);
    await settle();
    assert.equal(h.redirects.length, 0);
    assert.deepEqual(h.calls.map(call => call.data.action), ['saveEssay']);
    assert.ok(h.controls.every(control => control.tagName === 'TEXTAREA' ? control.readOnly : control.disabled));
    saved(response());
    await settle();
    assert.equal(h.calls.length, 1);
    assert.equal(h.redirects.length, 1);
});
