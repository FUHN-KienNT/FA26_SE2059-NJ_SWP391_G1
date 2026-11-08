const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/webapp/js/publish-confirmation.js'), 'utf8');

function fixture(type = 'assignment') {
    const nodes = [];
    let capture;
    function element(tag) {
        const listeners = new Map();
        const node = {
            tag, children: [], dataset: {}, open: false,
            append(...children) { this.children.push(...children); },
            prepend(child) { this.children.unshift(child); },
            setAttribute(name, value) { this[name] = value; },
            hasAttribute(name) { return name === 'data-confirm-publish' && this.dataset.confirmPublish != null; },
            addEventListener(name, handler) { listeners.set(name, handler); },
            fire(name, event = {}) { listeners.get(name)?.(event); },
            focus() {},
            showModal() { this.open = true; },
            close() { this.open = false; this.fire('close'); },
            querySelector() { return nodes.find(item => item.tag === 'button'); }
        };
        nodes.push(node);
        return node;
    }
    const body = element('body');
    const form = element('form');
    form.id = 'assignment-form';
    form.isConnected = true;
    form.fields = new Map([['title', 'Tình huống đang soạn'], ['csrf', 'token'], ['targetClassIds', '9,10']]);
    form.reportValidity = () => true;
    form.closest = () => null;
    const publish = element('button');
    publish.dataset.confirmPublish = type;
    publish.disabled = false;
    const save = element('button');
    let submissions = 0;
    let editorRuns = 0;
    let lastSubmitter;
    function submit(submitter) {
        const event = {
            target: form, submitter, defaultPrevented: false,
            preventDefault() { this.defaultPrevented = true; },
            stopImmediatePropagation() { this.stopped = true; }
        };
        capture(event);
        if (!event.stopped) {
            editorRuns++;
            form.onSubmit?.(event);
        }
        if (!event.defaultPrevented) { submissions++; lastSubmitter = submitter; }
        return event;
    }
    form.requestSubmit = submit;
    const document = {
        body,
        createElement: element,
        getElementById: id => nodes.find(node => node.id === id) || null,
        addEventListener(name, handler, useCapture) {
            assert.equal(name, 'submit');
            assert.equal(useCapture, true);
            capture = handler;
        }
    };
    class FormDataFixture {
        constructor(owner) { this.fields = owner.fields; }
        [Symbol.iterator]() { return this.fields[Symbol.iterator](); }
    }
    vm.runInNewContext(source, { document, FormData: FormDataFixture, URLSearchParams });
    return {
        nodes, form, publish, save, submit,
        get dialog() { return nodes.find(node => node.tag === 'dialog'); },
        get confirm() { return nodes.find(node => node.textContent === 'Có, công bố'); },
        get cancel() { return nodes.find(node => node.textContent === 'Không, quay lại'); },
        get submissions() { return submissions; },
        get editorRuns() { return editorRuns; },
        get lastSubmitter() { return lastSubmitter; }
    };
}

test('assignment publication waits for confirmation and resubmits the original publish action once', () => {
    const page = fixture();
    page.submit(page.publish);
    assert.equal(page.dialog.open, true);
    assert.equal(page.submissions, 0);
    assert.equal(page.editorRuns, 0);
    page.confirm.fire('click');
    assert.equal(page.dialog.open, false);
    assert.equal(page.submissions, 1);
    assert.equal(page.editorRuns, 1);
    assert.equal(page.lastSubmitter, page.publish);
    page.confirm.fire('click');
    assert.equal(page.submissions, 1);
});

test('cancelling keeps authoring data intact and saving a draft never opens publication confirmation', () => {
    const page = fixture();
    page.submit(page.publish);
    page.cancel.fire('click');
    assert.equal(page.submissions, 0);
    assert.equal(page.form.fields.get('title'), 'Tình huống đang soạn');
    assert.equal(page.publish.disabled, false);
    page.submit(page.save);
    assert.equal(page.submissions, 1);
    assert.equal(page.lastSubmitter, page.save);
    page.submit(null);
    assert.equal(page.dialog.open, false);
});

test('publishing grades shows the evaluation confirmation and rejects a changed form', () => {
    const page = fixture('evaluation');
    page.submit(page.publish);
    assert.match(page.nodes.find(node => node.tag === 'h2').textContent, /công bố điểm/);
    page.form.fields.set('score_1_1', '3.5');
    page.confirm.fire('click');
    assert.equal(page.submissions, 0);
    assert.match(page.nodes.find(node => node.id === 'publish-confirmation-notice').textContent, /Nội dung đã thay đổi/);
    page.submit(page.publish);
    page.confirm.fire('click');
    assert.equal(page.submissions, 1);
});

test('existing lifecycle windows do not open a second confirmation; direct confirmation pages do', () => {
    const modal = fixture();
    modal.form.closest = () => ({ open: true });
    modal.submit(modal.publish);
    assert.equal(modal.dialog, undefined);
    assert.equal(modal.submissions, 1);
    const direct = fixture();
    direct.form.id = 'lifecycle-form';
    direct.form.querySelector = () => direct.publish;
    direct.submit(null);
    assert.equal(direct.dialog.open, true);
    assert.equal(direct.submissions, 0);
});

test('invalid or detached forms cannot be published through the confirmation window', () => {
    const invalid = fixture();
    invalid.form.reportValidity = () => false;
    invalid.submit(invalid.publish);
    assert.equal(invalid.dialog, undefined);
    assert.equal(invalid.submissions, 0);
    const detached = fixture();
    detached.submit(detached.publish);
    detached.form.isConnected = false;
    detached.confirm.fire('click');
    assert.equal(detached.submissions, 0);
});

test('custom-validated authoring can fill pending points after confirmation before validating', () => {
    const page = fixture();
    page.form.noValidate = true;
    page.form.fields.set('pending_points', '');
    let validations = 0;
    page.form.reportValidity = () => {
        validations++;
        return page.form.fields.get('pending_points') !== '';
    };
    page.form.onSubmit = event => {
        // The editor owns this preprocessing; confirmation must not block it.
        page.form.fields.set('pending_points', '5.75');
        if (!page.form.reportValidity()) event.preventDefault();
    };

    page.submit(page.publish);
    assert.equal(page.dialog.open, true);
    assert.equal(validations, 0);
    assert.equal(page.editorRuns, 0);
    assert.equal(page.form.fields.get('pending_points'), '');
    assert.equal(page.submissions, 0);

    page.confirm.fire('click');
    assert.equal(validations, 1);
    assert.equal(page.editorRuns, 1);
    assert.equal(page.form.fields.get('pending_points'), '5.75');
    assert.equal(page.submissions, 1);
});

test('confirmation does not bypass rejection by a custom authoring validator', () => {
    const page = fixture();
    page.form.noValidate = true;
    page.form.onSubmit = event => event.preventDefault();

    page.submit(page.publish);
    assert.equal(page.submissions, 0);
    page.confirm.fire('click');
    assert.equal(page.editorRuns, 1);
    assert.equal(page.submissions, 0);
});
