const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const code = fs.readFileSync(path.join(__dirname, '../../main/webapp/js/subject-edit.js'), 'utf8');

function harness(createOptions = null) {
  const event = () => ({ prevented: false, preventDefault() { this.prevented = true; } });
  const element = () => ({ events: {}, addEventListener(name, handler) { this.events[name] = handler; } });
  const form = (method, status = false) => Object.assign(element(), {
    method, dataset: { message: 'Confirm status' }, buttons: [{ disabled: false }], confirmed: { value: 'false' },
    hasAttribute: () => status, querySelector() { return this.confirmed; }, querySelectorAll() { return this.buttons; }
  });
  const metadata = form('post'), registration = form('post'), status = form('post', true), picker = form('get');
  const selected = Object.assign(element(), { value: '2' });
  picker.dataset.loadedTerm = '2'; picker.querySelector = () => selected; picker.requests = 0;
  picker.requestSubmit = () => { picker.requests++; picker.lastEvent = event(); picker.events.submit(picker.lastEvent); };
  const feedback = { hidden: true, setAttribute(name, value) { this[name] = value; }, focus() { this.focused = true; }, scrollIntoView() { this.scrolled = true; } };
  const savedFeedback = { focus() { this.focused = true; }, scrollIntoView() { this.scrolled = true; } };
  const answers = [], windowEvents = {};
  const createOpening = createOptions && Object.assign(element(), { checked: createOptions.checked !== false, disabled: false });
  const initialStatus = Object.assign(element(), { value: createOptions?.status || 'ACTIVE' });
  const requiredFields = [{ required: false }, { required: false }, { required: false }];
  const openingFields = { disabled: false, querySelector: () => ({ options: { length: createOptions?.noTerms ? 1 : 2 } }), querySelectorAll: () => requiredFields };
  const unavailable = { hidden: true };
  vm.runInNewContext(code, {
    document: {
      querySelectorAll: () => [metadata, registration, status, picker],
      querySelector: selector => ({
        '.subject-term-picker': picker, '[data-subject-feedback]': savedFeedback,
        '[data-create-opening]': createOpening, '[data-create-opening-fields]': openingFields,
        '[name="initialStatus"]': initialStatus, '[data-create-opening-unavailable]': unavailable
      })[selector] || null,
      getElementById: () => feedback
    },
    window: { confirm: () => answers.shift(), addEventListener: (name, handler) => { windowEvents[name] = handler; } }
  });
  return { event, metadata, registration, status, picker, selected, feedback, savedFeedback, answers, windowEvents,
    createOpening, initialStatus, requiredFields, openingFields, unavailable };
}

test('selecting a different term navigates and rejects stale saves without disabling controls', () => {
  const h = harness(); h.selected.value = '9'; h.selected.events.change();
  assert.equal(h.picker.requests, 1); assert.equal(h.picker.lastEvent.prevented, false);
  assert.equal(h.registration.buttons[0].disabled, false);
  const save = h.event(); h.registration.events.submit(save);
  assert.equal(save.prevented, true); assert.equal(h.feedback.hidden, false);
});

test('cancelling term navigation restores the loaded term and preserves unsaved inputs', () => {
  const h = harness(); h.registration.events.input(); h.selected.value = '9'; h.answers.push(false);
  h.selected.events.change();
  assert.equal(h.picker.lastEvent.prevented, true); assert.equal(h.selected.value, '2');
  assert.equal(h.registration.buttons[0].disabled, false);
  const leave = h.event(); h.windowEvents.beforeunload(leave); assert.equal(leave.prevented, true);
});

test('accepting navigation clears the leave warning without locking controls', () => {
  const h = harness(); h.metadata.events.input(); h.selected.value = '9'; h.answers.push(true);
  h.selected.events.change();
  assert.equal(h.picker.lastEvent.prevented, false);
  assert.equal(h.metadata.buttons[0].disabled, false);
  const leave = h.event(); h.windowEvents.beforeunload(leave); assert.equal(leave.prevented, false);
});

test('an unloaded selection cannot silently save even without a change event', () => {
  const h = harness(); h.selected.value = '9'; const save = h.event();
  h.registration.events.submit(save);
  assert.equal(save.prevented, true); assert.equal(h.feedback.hidden, false);
  assert.match(h.feedback.textContent, /dữ liệu chưa thay đổi/);
  assert.equal(h.feedback.focused, true);
});

test('saving the loaded term is allowed and local server feedback receives focus', () => {
  const h = harness(); h.registration.events.input(); const save = h.event();
  h.registration.events.submit(save);
  assert.equal(save.prevented, false);
  assert.equal(h.savedFeedback.focused, true); assert.equal(h.savedFeedback.scrolled, true);
});

test('status confirmation still cancels or authorizes the requested operation', () => {
  const h = harness(); h.answers.push(false); const cancelled = h.event(); h.status.events.submit(cancelled);
  assert.equal(cancelled.prevented, true); assert.equal(h.status.confirmed.value, 'false');
  h.answers.push(true); const accepted = h.event(); h.status.events.submit(accepted);
  assert.equal(accepted.prevented, false); assert.equal(h.status.confirmed.value, 'true');
});

test('submitting the term already displayed is a no-op and saving remains available', () => {
  const h = harness(); h.registration.events.input(); h.picker.requestSubmit();
  assert.equal(h.picker.lastEvent.prevented, true);
  assert.equal(h.feedback.hidden, true); assert.equal(h.registration.buttons[0].disabled, false);
  const save = h.event(); h.registration.events.submit(save); assert.equal(save.prevented, false);
});

test('returning from browser history restores the displayed term and permits saving', () => {
  const h = harness(); h.selected.value = '9'; h.selected.events.change();
  h.windowEvents.pageshow({ persisted: true });
  assert.equal(h.selected.value, '2'); assert.equal(h.feedback.hidden, true);
  const save = h.event(); h.registration.events.submit(save); assert.equal(save.prevented, false);
});

test('create requires term and dates only while initial term setup is selected', () => {
  const h = harness({});
  assert.equal(h.openingFields.disabled, false);
  assert.equal(h.requiredFields.every(field => field.required), true);
  h.createOpening.checked = false; h.createOpening.events.change();
  assert.equal(h.openingFields.disabled, true);
  assert.equal(h.requiredFields.every(field => !field.required), true);
  h.createOpening.checked = true; h.createOpening.events.change();
  assert.equal(h.openingFields.disabled, false);
});

test('creating an inactive subject disables opening it for registration', () => {
  const h = harness({}); h.initialStatus.value = 'INACTIVE'; h.initialStatus.events.change();
  assert.equal(h.createOpening.checked, false); assert.equal(h.createOpening.disabled, true);
  assert.equal(h.openingFields.disabled, true); assert.equal(h.unavailable.hidden, false);
});

test('without eligible terms create remains available for catalog-only creation', () => {
  const h = harness({ noTerms: true });
  assert.equal(h.createOpening.disabled, true); assert.equal(h.createOpening.checked, false);
  assert.equal(h.openingFields.disabled, true);
  assert.equal(h.metadata.buttons[0].disabled, false);
});
