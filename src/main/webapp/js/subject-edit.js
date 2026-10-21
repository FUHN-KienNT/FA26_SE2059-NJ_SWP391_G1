(() => {
  'use strict';
  const forms = document.querySelectorAll('.admin-subjects form');
  const dirty = new Set();
  const createOpening = document.querySelector('[data-create-opening]');
  if (createOpening) {
    const fields = document.querySelector('[data-create-opening-fields]');
    const initialStatus = document.querySelector('[name="initialStatus"]');
    const unavailable = document.querySelector('[data-create-opening-unavailable]');
    const availableTerm = fields.querySelector('select[name="termId"]').options.length > 1;
    const syncOpening = () => {
      const allowed = availableTerm && initialStatus.value === 'ACTIVE';
      createOpening.disabled = !allowed;
      if (!allowed) createOpening.checked = false;
      fields.disabled = !allowed || !createOpening.checked;
      fields.querySelectorAll('[data-create-opening-required]').forEach(field => { field.required = !fields.disabled; });
      unavailable.hidden = allowed;
    };
    createOpening.addEventListener('change', syncOpening);
    initialStatus.addEventListener('change', syncOpening);
    syncOpening();
  }
  const picker = document.querySelector('.subject-term-picker');
  const selectedTerm = picker && picker.querySelector('select[name="termId"]');
  const termFeedback = document.getElementById('subject-term-feedback');
  const showTermFeedback = message => {
    if (!termFeedback) return;
    termFeedback.hidden = false;
    termFeedback.setAttribute('role', 'alert');
    termFeedback.textContent = message;
    termFeedback.focus({ preventScroll: true });
    termFeedback.scrollIntoView({ block: 'center' });
  };
  forms.forEach(form => {
    if (form.method.toLowerCase() !== 'post') return;
    form.addEventListener('change', () => dirty.add(form));
    form.addEventListener('input', () => dirty.add(form));
    form.addEventListener('submit', event => {
      if (picker && selectedTerm.value !== picker.dataset.loadedTerm) {
        event.preventDefault();
        showTermFeedback('Học kỳ đã chọn chưa được tải. Vui lòng tải đăng ký của kỳ trước khi lưu; dữ liệu chưa thay đổi.');
        return;
      }
      if ([...dirty].some(other => other !== form)
          && !window.confirm('Phần khác có thay đổi chưa lưu. Tiếp tục chỉ lưu phần này?')) {
        event.preventDefault();
        return;
      }
      if (form.hasAttribute('data-subject-status')) {
        if (!window.confirm(form.dataset.message)) {
          event.preventDefault();
          return;
        }
        form.querySelector('[data-status-confirmed]').value = 'true';
      }
      dirty.clear();
    });
  });
  if (picker && selectedTerm) {
    picker.addEventListener('submit', event => {
      if (selectedTerm.value === picker.dataset.loadedTerm) {
        event.preventDefault();
        return;
      }
      if (dirty.size && !window.confirm('Đổi kỳ đang xem sẽ bỏ các thay đổi chưa lưu. Tiếp tục tải dữ liệu của kỳ đã chọn?')) {
        event.preventDefault();
        selectedTerm.value = picker.dataset.loadedTerm;
        return;
      }
      dirty.clear();
    });
    selectedTerm.addEventListener('change', () => picker.requestSubmit());
  }
  const feedback = document.querySelector('[data-subject-feedback]');
  if (feedback) {
    feedback.focus({ preventScroll: true });
    feedback.scrollIntoView({ block: 'center' });
  }
  window.addEventListener('beforeunload', event => {
    if (dirty.size) { event.preventDefault(); event.returnValue = ''; }
  });
  window.addEventListener('pageshow', event => {
    if (event.persisted && picker && selectedTerm) {
      selectedTerm.value = picker.dataset.loadedTerm;
      if (termFeedback) termFeedback.hidden = true;
    }
  });
})();
