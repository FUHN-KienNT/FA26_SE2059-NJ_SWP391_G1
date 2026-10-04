/* Shared UI only: authentication and business rules remain on the server. */
document.addEventListener('DOMContentLoaded',()=>{
 document.querySelectorAll('.ams-menu-toggle').forEach(button=>button.addEventListener('click',()=>{const hidden=document.body.classList.toggle('ams-nav-hidden');button.setAttribute('aria-expanded',String(!hidden));}));
 document.querySelectorAll('form[data-confirm]').forEach(form=>form.addEventListener('submit',event=>{if(!window.confirm(form.dataset.confirm))event.preventDefault();}));
 document.querySelectorAll('form[data-live-search]').forEach(form=>{
  const field=form.querySelector('input[name="keyword"]');let timer,composing=false;if(!field)return;
  const key='ams-search-focus:'+location.pathname;
  try{if(sessionStorage.getItem(key)==='1'){field.focus();field.setSelectionRange(field.value.length,field.value.length);sessionStorage.removeItem(key);}}catch(ignore){}
  const submit=()=>{try{sessionStorage.setItem(key,'1');}catch(ignore){}if(form.reportValidity())form.requestSubmit();};
  field.addEventListener('compositionstart',()=>{composing=true;clearTimeout(timer);});field.addEventListener('compositionend',()=>{composing=false;clearTimeout(timer);timer=setTimeout(submit,650);});field.addEventListener('input',()=>{clearTimeout(timer);if(!composing)timer=setTimeout(submit,650);});
  form.addEventListener('submit',()=>clearTimeout(timer));form.querySelectorAll('select').forEach(select=>select.addEventListener('change',()=>{clearTimeout(timer);form.requestSubmit();}));
 });
});
