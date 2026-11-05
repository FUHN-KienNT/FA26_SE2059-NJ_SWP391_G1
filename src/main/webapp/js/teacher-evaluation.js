(() => {
    'use strict';
    const form = document.querySelector('[data-evaluation-form]');
    const error = document.querySelector('[data-evaluation-error]');
    if (error) error.focus();
    if (!form) return;
    const scores = [...document.querySelectorAll('[data-evaluation-score]')];
    const total = document.querySelector('[data-evaluation-total]');
    const progress = document.querySelector('[data-evaluation-progress]');
    const confirmation = document.getElementById('confirm-release');

    // Sum integer hundredths, preserving the distinction between zero and an empty score.
    const hundredths = value => {
        if (!/^[0-9]{1,3}(\.[0-9]{1,2})?$/.test(value)) return null;
        const [whole, fraction = ''] = value.split('.');
        return Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
    };
    const validate = input => {
        const amount = hundredths(input.value);
        const maximum = hundredths(input.dataset.maximum);
        const invalid = input.value !== '' && (amount === null || amount > maximum);
        input.setCustomValidity(invalid ? `Nhập điểm từ 0 đến ${input.dataset.maximum}, có tối đa 2 chữ số thập phân.` : '');
        return invalid;
    };
    const update = () => {
        let sum = 0;
        let count = 0;
        let invalid = false;
        for (const input of scores) {
            if (validate(input)) invalid = true;
            else if (input.value !== '') { sum += hundredths(input.value); count++; }
        }
        if (scores.length && total) total.textContent = invalid ? 'Kiểm tra điểm' : (sum / 100).toFixed(2).replace(/\.?0+$/, '');
        if (scores.length && progress) progress.textContent = `Đã chấm ${count}/${progress.dataset.requirementCount} yêu cầu`;
        if (scores.length) document.querySelectorAll('[data-question-total]').forEach(output => {
            const taskScores = scores.filter(input => input.name.startsWith(`score_${output.dataset.questionTotal}_`));
            const entered = taskScores.filter(input => input.value !== '');
            output.textContent = entered.some(validate) ? 'Kiểm tra điểm' : entered.length === 0 ? 'Chưa chấm'
                : (entered.reduce((sum, input) => sum + hundredths(input.value), 0) / 100).toFixed(2).replace(/\.?0+$/, '');
        });
    };
    scores.forEach(input => input.addEventListener('input', update));
    // Clear action-specific errors before native validation: an incomplete completion
    // must still allow saving a draft, and an unchecked release must still allow reopening.
    document.querySelectorAll('button[form="evaluation-form"][name="action"]').forEach(button => {
        button.addEventListener('click', () => {
            scores.forEach(validate);
            if (confirmation) confirmation.setCustomValidity('');
        });
    });
    if (confirmation) confirmation.addEventListener('change', () => confirmation.setCustomValidity(''));
    form.addEventListener('submit', event => {
        const action = event.submitter?.value;
        if (!action) { event.preventDefault(); return; }
        if (action === 'completeEvaluation') {
            const missing = scores.find(input => input.value === '');
            if (missing) {
                event.preventDefault();
                missing.setCustomValidity('Nhập điểm cho yêu cầu này trước khi hoàn tất chấm. Có thể nhập 0.');
                missing.reportValidity();
                return;
            }
        }
        if (action === 'releaseEvaluation' && confirmation && !confirmation.checked) {
            event.preventDefault();
            confirmation.setCustomValidity('Vui lòng xác nhận công bố kết quả cho sinh viên.');
            confirmation.reportValidity();
        }
    });
    update();
})();
