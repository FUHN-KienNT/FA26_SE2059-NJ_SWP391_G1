document.addEventListener('DOMContentLoaded', () => {
  const form = document.querySelector('[data-user-detail]');
  if (!form) return;
  const role = form.querySelector('[name="role"]');
  const eligibility = form.querySelector('#teaching-eligibility');
  const expertSubjects = form.querySelector('#expert-subjects');
  const update = () => {
    const teacher = role.value === 'TEACHER';
    eligibility.hidden = !teacher;
    eligibility.disabled = !teacher;
    if (expertSubjects) expertSubjects.hidden = role.value !== 'EXPERT';
  };
  role.addEventListener('change', update);
  update();
  const search = form.querySelector('#eligibility-search');
  const rows = Array.from(eligibility.querySelectorAll('.eligibility-subject'));
  const empty = eligibility.querySelector('.eligibility-empty');
  const normalize = text => text.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd').replace(/Đ/g, 'D').toLocaleLowerCase('vi').trim();
  search.addEventListener('input', () => {
    const query = normalize(search.value);
    rows.forEach(row => { row.hidden = !normalize(row.textContent).includes(query); });
    empty.hidden = rows.some(row => !row.hidden);
  });
  search.addEventListener('keydown', event => {
    if (event.key === 'Enter') event.preventDefault();
  });
  const snapshot = () => JSON.stringify(Array.from(form.elements)
    .filter(field => ['fullName', 'email', 'role', 'status', 'subjects'].includes(field.name))
    .map(field => [field.name, field.value, ['checkbox', 'radio'].includes(field.type) ? field.checked : null]));
  const expertSearch = form.querySelector('#expert-subject-search');
  if (expertSearch) {
    const subjectRows = Array.from(expertSubjects.querySelectorAll('[data-expert-subject]'));
    const noMatch = expertSubjects.querySelector('#expert-subject-empty');
    expertSearch.addEventListener('input', () => {
      const query = normalize(expertSearch.value);
      subjectRows.forEach(row => {
        const title = row.cells[0].textContent + ' ' + row.cells[1].textContent;
        row.hidden = !normalize(title).includes(query);
      });
      noMatch.hidden = subjectRows.some(row => !row.hidden);
    });
    expertSearch.addEventListener('keydown', event => {
      if (event.key === 'Enter') event.preventDefault();
    });
  }
  const initial = snapshot();
  const dialog = document.querySelector('#unsaved-account-dialog');
  let destination;
  form.querySelectorAll('[data-manage-assignment]').forEach(link => {
    link.addEventListener('click', event => {
      // Opening a separate tab leaves this form intact.
      if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || snapshot() === initial) return;
      event.preventDefault();
      destination = link;
      dialog.showModal();
    });
  });
  dialog.querySelector('[data-unsaved="stay"]').addEventListener('click', () => dialog.close());
  dialog.querySelector('[data-unsaved="discard"]').addEventListener('click', () => {
    dialog.close();
    location.assign(destination.href);
  });
  dialog.querySelector('[data-unsaved="save"]').addEventListener('click', () => {
    dialog.close();
    if (!form.reportValidity()) return;
    form.elements.continueSubjectId.value = destination.dataset.subjectId;
    form.requestSubmit();
  });
});
