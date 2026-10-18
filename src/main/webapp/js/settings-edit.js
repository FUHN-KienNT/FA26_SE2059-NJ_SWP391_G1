document.addEventListener('DOMContentLoaded', () => {
  const form = document.querySelector('form[data-setting-edit]');
  if (!form) return;
  form.addEventListener('submit', event => {
    const selected = form.elements.namedItem('active').value;
    if (selected === form.dataset.currentActive) return;
    const message = selected === 'false'
      ? 'Xác nhận vô hiệu hóa thiết lập này? Giá trị sẽ không còn là lựa chọn cho dữ liệu mới; dữ liệu đã lưu vẫn được giữ.'
      : 'Xác nhận kích hoạt thiết lập này?';
    if (!window.confirm(message)) event.preventDefault();
  });
});
