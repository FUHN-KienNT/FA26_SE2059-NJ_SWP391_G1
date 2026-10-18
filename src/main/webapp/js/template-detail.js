document.addEventListener('DOMContentLoaded', () => {
  const input = document.querySelector('#template-maximum-score');
  if (!input || input.readOnly) return;
  const error = document.querySelector('#template-score-error');
  const validate = () => {
    const value = input.value.trim();
    const valid = /^[0-9]{1,3}$/.test(value) && Number(value) >= 1 && Number(value) <= 100;
    const message = valid ? '' : 'Điểm tối đa phải là số nguyên từ 1 đến 100.';
    input.setCustomValidity(message);
    if (error) {
      error.hidden = valid;
      error.textContent = message;
    }
    input.setAttribute('aria-invalid', String(!valid));
  };
  input.addEventListener('input', validate);
  input.addEventListener('invalid', validate);
});
