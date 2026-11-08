const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/webapp/js/teacher-evaluation.js'), 'utf8');

function element(dataset = {}) {
    const listeners = new Map();
    const classes = new Set();
    return {
        dataset,
        hidden: false,
        value: '',
        listeners,
        classes,
        classList: {
            toggle(name, enabled) {
                if (enabled) classes.add(name);
                else classes.delete(name);
            }
        },
        addEventListener(name, callback) { listeners.set(name, callback); },
        setAttribute(name, value) { this[name] = value; },
        hasAttribute(name) { return name === 'data-saved-value' && 'savedValue' in this.dataset; },
        setCustomValidity(value) { this.validationMessage = value; },
        focus() {},
        fire(name, event = {}) { return listeners.get(name)?.(event); }
    };
}

test('requirement tabs remain usable while grading is locked and no form is rendered', () => {
    const tabs = [element({ gradeTab: '1_1' }), element({ gradeTab: '1_2' })];
    const answers = [element({ answerPanel: '1_1' }), element({ answerPanel: '1_2' })];
    const scores = [element({ scorePanel: '1_1' }), element({ scorePanel: '1_2' })];
    const document = {
        querySelector() { return null; },
        getElementById() { return null; },
        querySelectorAll(selector) {
            return {
                '[data-grade-tab]': tabs,
                '[data-answer-panel]': answers,
                '[data-score-panel]': scores
            }[selector] || [];
        }
    };

    vm.runInNewContext(source, { document });
    tabs[1].fire('click');

    assert.equal(tabs[1]['aria-selected'], 'true');
    assert.equal(answers[0].hidden, true);
    assert.equal(answers[1].hidden, false);
    assert.equal(scores[1].hidden, false);
});

test('restore uses persisted feedback and AI provenance even after a rejected form post', () => {
    const feedback = element({ aiFeedback: 'feedbackAi', savedValue: 'Persisted teacher feedback' });
    feedback.name = 'feedback';
    feedback.value = 'Unsaved AI proposal returned after validation error';
    const aiFlag = element({ savedValue: 'false' });
    aiFlag.name = 'feedbackAi';
    aiFlag.value = 'true';
    const label = element({ aiLabel: 'feedbackAi' });
    const reset = element();
    const form = element();
    form.elements = [feedback, aiFlag];
    form.elements.namedItem = name => form.elements.find(field => field.name === name);
    const document = {
        getElementById() { return null; },
        querySelector(selector) {
            return { '[data-evaluation-form]': form, '[data-reset-evaluation]': reset }[selector] || null;
        },
        querySelectorAll(selector) {
            return { '[data-ai-feedback]': [feedback], '[data-ai-label]': [label] }[selector] || [];
        }
    };

    vm.runInNewContext(source, { document, confirm: () => true });
    reset.fire('click');

    assert.equal(feedback.value, 'Persisted teacher feedback');
    assert.equal(aiFlag.value, 'false');
    assert.equal(feedback.classes.has('ai-feedback'), false);
    assert.equal(label.hidden, true);
});

function aiFixture(response) {
    const score = element({ maximum: '2', key: '1_1' });
    score.name = 'score_1_1';
    score.value = '0.5';
    const feedback = element({ aiFeedback: 'ai_1_1' });
    feedback.name = 'requirementFeedback_1_1';
    feedback.value = 'Nhận xét đang nhập';
    const overall = element({ aiFeedback: 'feedbackAi' });
    overall.name = 'feedback';
    overall.value = 'Nhận xét chung đang nhập';
    const aiFlag = element();
    aiFlag.name = 'ai_1_1';
    aiFlag.value = 'false';
    const overallFlag = element();
    overallFlag.name = 'feedbackAi';
    overallFlag.value = 'false';
    const button = element();
    const notice = element();
    const form = element();
    form.action = '';
    form.getAttribute = name => name === 'action' ? '/ams/teacher/submissions' : null;
    form.elements = [score, feedback, overall, aiFlag, overallFlag];
    form.elements.namedItem = name => form.elements.find(field => field.name === name);
    const requests = [];
    const document = {
        getElementById(name) { return name === 'evaluation-notification' ? notice : null; },
        querySelector(selector) {
            return { '[data-evaluation-form]': form, '[data-ai-grade]': button }[selector] || null;
        },
        querySelectorAll(selector) {
            return {
                '[data-evaluation-score]': [score],
                '[data-ai-feedback]': [feedback, overall]
            }[selector] || [];
        }
    };

    class FormDataFixture {
        constructor(owner) { this.fields = new Map(owner.elements.map(field => [field.name, field.value])); }
        set(name, value) { this.fields.set(name, value); }
        [Symbol.iterator]() { return this.fields[Symbol.iterator](); }
    }

    vm.runInNewContext(source, {
        document,
        confirm: () => true,
        FormData: FormDataFixture,
        URLSearchParams,
        AbortController,
        setTimeout,
        clearTimeout,
        fetch: async (url, options) => {
            requests.push({ url, options });
            return response;
        }
    });

    return { score, feedback, overall, aiFlag, overallFlag, button, notice, requests };
}

test('AI posts to the action attribute when submit-button names shadow form.action', async () => {
    const fixture = aiFixture({
        ok: true,
        status: 200,
        redirected: false,
        headers: { get: () => 'application/json;charset=UTF-8' },
        json: async () => ({
            marks: [{ task_order: 1, requirement_order: 1, score: 1.5, feedback: 'Đề xuất AI' }],
            feedback: 'Nhận xét chung AI'
        })
    });

    await fixture.button.fire('click');

    assert.equal(fixture.requests.length, 1);
    assert.equal(fixture.requests[0].url, '/ams/teacher/submissions');
    assert.equal(fixture.requests[0].options.body.get('action'), 'aiEvaluation');
    assert.equal(fixture.score.value, '1.5');
    assert.equal(fixture.feedback.value, 'Đề xuất AI');
    assert.equal(fixture.aiFlag.value, 'true');
    assert.equal(fixture.button.disabled, false);
});

test('AI expired sessions and HTML errors keep edits and show recoverable Vietnamese notices', async () => {
    for (const response of [
        { status: 200, redirected: true },
        { status: 401, redirected: false },
        { status: 403, redirected: false },
        { status: 500, redirected: false }
    ]) {
        const fixture = aiFixture({
            ...response,
            ok: false,
            headers: { get: () => 'text/html;charset=UTF-8' },
            json: async () => { throw new Error('HTML must not be parsed as JSON'); }
        });

        await fixture.button.fire('click');

        assert.equal(fixture.score.value, '0.5');
        assert.equal(fixture.feedback.value, 'Nhận xét đang nhập');
        assert.equal(fixture.aiFlag.value, 'false');
        assert.equal(fixture.notice.role, 'alert');
        assert.match(fixture.notice.textContent, /Vui lòng|Hãy|Vui lòng/);
        assert.doesNotMatch(fixture.notice.textContent, /Unexpected|HTML must/);
        assert.equal(fixture.button.disabled, false);
    }
});

test('AI malformed JSON is reported without exposing the raw parser error', async () => {
    const fixture = aiFixture({
        ok: true,
        status: 200,
        redirected: false,
        headers: { get: () => 'application/json' },
        json: async () => { throw new SyntaxError("Unexpected token '<'"); }
    });

    await fixture.button.fire('click');

    assert.match(fixture.notice.textContent, /Không đọc được đề xuất AI/);
    assert.doesNotMatch(fixture.notice.textContent, /Unexpected token/);
    assert.equal(fixture.feedback.value, 'Nhận xét đang nhập');
});
