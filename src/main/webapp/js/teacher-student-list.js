/* Open the two enrollment actions as separate accessible modal dialogs. */
document.addEventListener('DOMContentLoaded', function initializeEnrollmentDialogs() {
    // Show the selected dialog and let the browser manage focus and Escape.
    function openDialog(dialog) {
        if (dialog && !dialog.open) dialog.showModal();
    }
    document.querySelectorAll('[data-open-dialog]').forEach(function bindDialogTrigger(button) {
        // Resolve the dialog from the clicked action, without submitting a form.
        button.addEventListener('click', function showEnrollmentAction() {
            openDialog(document.getElementById(button.dataset.openDialog));
        });
    });
    document.querySelectorAll('[data-close-dialog]').forEach(function bindDialogClose(button) {
        // Closing a modal leaves the current class and roster in place.
        button.addEventListener('click', function closeEnrollmentAction() {
            button.closest('dialog').close();
        });
    });
    document.querySelectorAll('dialog[data-reopen]').forEach(openDialog);
});
