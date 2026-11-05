(() => {
    'use strict';
    const form = document.getElementById('essay-work');

    if (!form) return;

    const status = document.getElementById('essay-save-status');
    const clock = document.getElementById('essay-countdown');
    const url = new URL(form.getAttribute('action'), location.href).href;
    const resultUrl =
        `${url}?assignmentId=${encodeURIComponent(form.elements.assignmentId.value)}&submissionId=${encodeURIComponent(form.elements.submissionId.value)}`;
    const requirements = [...form.querySelectorAll('.essay-requirement')];
    const dialog = document.getElementById('diagram-dialog');
    const diagramStatus = document.getElementById('diagram-status');
    const frameHost = document.getElementById('diagram-frame');
    const plantDialog = document.getElementById('plantuml-dialog');
    const plantSource = document.getElementById('plantuml-source');
    const plantStatus = document.getElementById('plantuml-status');
    const plantPreview = document.getElementById('plantuml-preview');
    const plantRender = document.getElementById('plantuml-render');
    const plantApply = document.getElementById('plantuml-apply');
    const plantUrl = new URL('diagrams/plantuml', url).href;
    const editorOrigin = 'https://embed.diagrams.net';
    let deadline = Number(form.dataset.deadline),
        serverTime = Number(form.dataset.serverTime),
        syncAt = performance.now();
    let dirty = true,
        submitting = false,
        blocked = false,
        completed = false,
        savePromise, debounce, activeEditor, frame, pendingXml;
    let plantEditor, plantRequest, plantGeneration = 0,
        renderedPlantSource = '',
        renderedPlantImage = '';
    const now = () => serverTime + performance.now() - syncAt;
    const content = () => ({
        format: 'ams-essay-v2',
        answers: requirements.map(row => ({
            task_order: Number(row.dataset.task),
            requirement_order: Number(row.dataset.requirement),
            text: row.querySelector('.essay-text')?.value || '',
            diagram_xml: row.querySelector('.diagram-xml')?.value || '',
            diagram_plantuml: row.querySelector('.diagram-plantuml')?.value || '',
            diagram_image: row.querySelector('.diagram-image')?.value || ''
        }))
    });

    function body(action) {
        const data = new URLSearchParams();

        for (const name of ['csrf', 'assignmentId', 'submissionId', 'answerRevision']) data.set(name, form.elements[name]
            .value);

        data.set('action', action);
        data.set('essayContent', JSON.stringify(content()));

        if (new TextEncoder().encode(data.toString()).length > 1_900_000)
            throw new Error('Bài làm vượt giới hạn dung lượng. Hãy giảm số lượng hoặc kích thước sơ đồ.');

        return data;
    }

    const lock = () => form.querySelectorAll('textarea,button').forEach(el => {
        if (el.tagName === 'TEXTAREA') el.readOnly = true;
        else el.disabled = true;
    });
    const unlock = () => form.querySelectorAll('textarea,button').forEach(el => {
        if (el.tagName === 'TEXTAREA') el.readOnly = false;
        else el.disabled = false;
    });

    function conflict() {
        blocked = true;
        clearTimeout(debounce);
        lock();
        closeEditor(true);
        status.textContent =
            'Bài làm đã thay đổi ở phiên khác. Nội dung trên trang chưa được lưu; hãy sao chép phần cần giữ trước khi tải lại. ';
        const link = document.createElement('a');
        link.href = resultUrl;
        link.textContent = 'Tải lại bài làm';
        status.append(link);
    }

    function received() {
        completed = true;
        dirty = false;
        closeEditor(true);
        location.assign(resultUrl);
    }

    function changed() {
        if (blocked || submitting) return;

        dirty = true;
        clearTimeout(debounce);
        status.textContent = 'Có thay đổi chưa lưu…';
        debounce = setTimeout(save, 650);
    }

    async function request(data) {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 20000);

        try {
            return await fetch(url, {
                method: 'POST',
                body: data,
                credentials: 'same-origin',
                cache: 'no-store',
                signal: controller.signal
            });
        } finally {
            clearTimeout(timeout);
        }
    }

    async function save() {
        if (blocked || submitting || savePromise || !dirty) return;

        if (now() >= deadline) {
            finish();
            return;
        }

        let payload;

        try {
            payload = body('saveEssay');
        } catch (error) {
            status.textContent = error.message;
            return;
        }

        dirty = false;
        status.textContent = 'Đang lưu bài làm…';
        savePromise = (async () => {
            const start = performance.now();

            try {
                const response = await request(payload);

                if (response.status === 409) {
                    conflict();
                    return;
                }

                if (!response.ok || !response.headers.get('content-type')?.includes('application/json'))
                    throw new Error(response.status === 400 ?
                        'Nội dung hoặc sơ đồ không hợp lệ. Hãy kiểm tra lại trước khi lưu.' :
                        'Chưa lưu được bài làm. Hãy giữ kết nối và thử lại.');

                const saved = await response.json();
                serverTime = saved.serverTime + (performance.now() - start) / 2;
                syncAt = performance.now();
                deadline = saved.deadline;
                form.elements.answerRevision.value = saved.revision;

                if (saved.finished) {
                    received();
                    return;
                }

                status.textContent = `Đã lưu lúc ${new Date().toLocaleTimeString('vi-VN')}.`;
            } catch (error) {
                dirty = true;
                status.textContent = error.message || 'Chưa lưu được bài làm.';
            }
        })();

        try {
            await savePromise;
        } finally {
            savePromise = null;

            if (dirty && !submitting && !blocked && now() < deadline) debounce = setTimeout(save, 5000);
        }
    }

    async function finish() {
        if (submitting || blocked || completed) return;

        // Keep the editor usable if an oversized draft is rejected before submission.
        let oversized = false;

        try {
            body('submitEssay');
        } catch (error) {
            if (now() < deadline) {
                status.textContent = error.message;
                return;
            }

            oversized = true;
        }

        submitting = true;
        clearTimeout(debounce);
        lock();
        closeEditor(true);
        status.textContent = now() >= deadline ? 'Đã hết giờ. Đang tự động nộp phần bài làm đã lưu…' : 'Đang nộp bài…';

        if (savePromise) await savePromise;

        if (blocked || completed) return;

        // The result page checks the server deadline and retains the last saved work.
        if (oversized) {
            received();
            return;
        }

        try {
            const response = await request(body('submitEssay'));

            if (response.status === 409) {
                conflict();
                return;
            }

            if ([400, 413].includes(response.status) && now() < deadline) {
                submitting = false;
                unlock();
                dirty = true;
                status.textContent =
                    'Bài chưa được nộp. Hãy kiểm tra nội dung hoặc giảm kích thước sơ đồ rồi lưu/nộp lại.';
                return;
            }

            if (!response.ok) throw new Error('Nộp bài chưa được xác nhận.');

            received();
        } catch (error) {
            status.textContent =
                `${error.message || 'Chưa nộp được bài.'} Máy chủ vẫn tự nộp phần đã lưu khi hết hạn. Đang thử lại…`;

            setTimeout(() => {
                submitting = false;
                finish();
            }, 5000);
        }
    }

    function closeEditor(force = false) {
        if (plantEditor && !closePlantEditor(force)) return false;

        if (!activeEditor) return true;

        if (!force && !confirm('Đóng trình vẽ? Thay đổi chưa nhấn Lưu trong trình vẽ sẽ không được giữ.')) return false;

        activeEditor = null;
        pendingXml = null;
        frame?.remove();
        frame = null;
        dialog.close();
        return true;
    }

    function send(message) {
        frame?.contentWindow.postMessage(JSON.stringify(message), editorOrigin);
    }

    function openEditor(row) {
        if (blocked || submitting || now() >= deadline) return;

        if (row.querySelector('.diagram-plantuml').value && !confirm(
                'Yêu cầu này đang có sơ đồ PlantUML. Nếu lưu bằng draw.io, sơ đồ cũ sẽ được thay thế. Tiếp tục?')) return;

        if (!closeEditor()) return;

        activeEditor = row;
        pendingXml = null;
        frameHost.replaceChildren();
        diagramStatus.textContent = 'Đang tải draw.io… Nhấn Lưu trong trình vẽ để đưa sơ đồ vào bài làm.';
        frame = document.createElement('iframe');
        frame.title = 'Trình vẽ draw.io';
        frame.referrerPolicy = 'no-referrer';
        frame.setAttribute('sandbox', 'allow-scripts allow-same-origin allow-modals allow-downloads');
        frame.src = `${editorOrigin}/?embed=1&proto=json&spin=1&saveAndExit=1&libraries=1&lang=vi`;
        frameHost.append(frame);
        dialog.showModal();
    }

    async function croppedPng(data) {
        if (!/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(data) || data.length > 8_000_000) throw new Error(
            'Trình vẽ chưa trả về ảnh PNG hợp lệ.');

        const image = new Image();
        image.src = data;
        await image.decode();
        const width = image.naturalWidth,
            height = image.naturalHeight;

        if (!width || !height || width > 4096 || height > 4096 || width * height > 8_000_000) throw new Error(
            'Sơ đồ quá lớn. Hãy thu nhỏ hoặc chia bớt nội dung.');

        const canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext('2d', {
            willReadFrequently: true
        });
        ctx.drawImage(image, 0, 0);
        const pixels = ctx.getImageData(0, 0, width, height).data;
        let left = width,
            right = -1,
            top = height,
            bottom = -1;

        for (let y = 0; y < height; y++)
            for (let x = 0; x < width; x++) {
                const at = (y * width + x) * 4;

                if (pixels[at + 3] > 15 && (pixels[at] < 250 || pixels[at + 1] < 250 || pixels[at + 2] < 250)) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }

        if (right < left) throw new Error('Sơ đồ đang trống. Hãy vẽ nội dung trước khi lưu.');

        const result = document.createElement('canvas');
        result.width = right - left + 17;
        result.height = bottom - top + 17;
        const out = result.getContext('2d');
        out.fillStyle = '#ffffff';
        out.fillRect(0, 0, result.width, result.height);
        out.drawImage(canvas, left, top, right - left + 1, bottom - top + 1, 8, 8, right - left + 1, bottom - top + 1);
        let png = result.toDataURL('image/png');
        const oversized = () => png.length > 349550 || result.width > 4096 || result.height > 4096 || result.width *
            result.height > 8_000_000;

        for (let i = 0; oversized() && i < 4; i++) {
            const smaller = document.createElement('canvas');
            smaller.width = Math.max(1, Math.floor(result.width * .8));
            smaller.height = Math.max(1, Math.floor(result.height * .8));
            smaller.getContext('2d').drawImage(result, 0, 0, smaller.width, smaller.height);
            result.width = smaller.width;
            result.height = smaller.height;
            result.getContext('2d').drawImage(smaller, 0, 0);
            png = result.toDataURL('image/png');
        }

        if (oversized()) throw new Error('Sơ đồ vẫn vượt giới hạn sau khi thu nhỏ. Hãy đơn giản hóa sơ đồ.');

        return png;
    }

    window.addEventListener('message', async event => {
        if (event.origin !== editorOrigin || !activeEditor || event.source !== frame?.contentWindow) return;

        let message;

        try {
            message = typeof event.data === 'string' ? JSON.parse(event.data) : event.data;
        } catch {
            return;
        }

        if (!message || typeof message !== 'object') return;

        if (message.event === 'init') {
            const xml = activeEditor.querySelector('.diagram-xml').value ||
                '<mxfile><diagram name="Trang-1"><mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/></root></mxGraphModel></diagram></mxfile>';
            send({
                action: 'load',
                xml,
                autosave: 0,
                title: 'Sơ đồ bài làm'
            });
            diagramStatus.textContent = 'Vẽ sơ đồ rồi nhấn Lưu ở thanh công cụ.';
        } else if (message.event === 'save') {
            if (typeof message.xml !== 'string' || message.xml.length > 150000) {
                diagramStatus.textContent = 'Sơ đồ quá lớn, hãy giảm nội dung.';
                return;
            }

            pendingXml = message.xml;
            diagramStatus.textContent = 'Đang xuất và cắt ảnh sơ đồ…';
            send({
                action: 'export',
                format: 'png',
                xml: pendingXml
            });
        } else if (message.event === 'export' && pendingXml && typeof message.data === 'string') {
            const row = activeEditor,
                xml = pendingXml;

            try {
                const png = await croppedPng(message.data);

                if (row !== activeEditor || blocked || submitting || now() >= deadline) return;

                row.querySelector('.diagram-xml').value = xml;
                row.querySelector('.diagram-image').value = png;
                row.querySelector('.diagram-plantuml').value = '';
                const preview = row.querySelector('.diagram-preview');
                preview.src = png;
                preview.hidden = false;
                row.querySelector('.remove-diagram').hidden = false;
                closeEditor(true);
                changed();
                save();
            } catch (error) {
                diagramStatus.textContent = error.message;
            }
        } else if (message.event === 'exit') closeEditor(false);
    });

    function clearPlantPreview() {
        renderedPlantSource = '';
        renderedPlantImage = '';
        plantApply.disabled = true;
        plantPreview.hidden = true;
        plantPreview.removeAttribute('src');
    }

    function closePlantEditor(force = false) {
        if (!plantEditor) return true;

        if (!force && plantSource.value !== plantEditor.querySelector('.diagram-plantuml').value &&
            !confirm('Đóng PlantUML? Nội dung chưa nhấn Đưa vào bài làm sẽ không được giữ.')) return false;

        plantGeneration++;
        plantRequest?.abort();
        plantRequest = null;
        plantEditor = null;
        clearPlantPreview();
        plantDialog.close();
        return true;
    }

    function openPlantEditor(row) {
        if (blocked || submitting || now() >= deadline) return;

        if (row.querySelector('.diagram-xml').value && !confirm(
                'Yêu cầu này đang có sơ đồ draw.io. Nếu đưa sơ đồ PlantUML vào bài làm, sơ đồ cũ sẽ được thay thế. Tiếp tục?'
                )) return;

        if (!closeEditor()) return;

        plantEditor = row;
        plantGeneration++;
        clearPlantPreview();
        plantRender.disabled = false;
        const source = row.querySelector('.diagram-plantuml').value;
        plantSource.value = source ||
            '@startuml\nactor "Sinh viên" as Student\nparticipant "Hệ thống" as System\nStudent -> System: Nộp bài\nSystem --> Student: Xác nhận đã nhận bài\n@enduml';

        if (source && row.querySelector('.diagram-image').value) {
            renderedPlantSource = source;
            renderedPlantImage = row.querySelector('.diagram-image').value;
            plantPreview.src = renderedPlantImage;
            plantPreview.hidden = false;
            plantApply.disabled = false;
        }

        plantStatus.textContent = 'Nhập mã PlantUML, nhấn Xem trước, rồi Đưa vào bài làm để lưu sơ đồ cho yêu cầu này.';
        plantDialog.showModal();
        plantSource.focus();
    }

    plantSource.addEventListener('input', () => {
        plantGeneration++;
        plantRequest?.abort();
        plantRequest = null;
        plantRender.disabled = false;
        clearPlantPreview();
        plantStatus.textContent = 'Mã đã thay đổi. Nhấn Xem trước để cập nhật sơ đồ.';
    });

    plantRender.addEventListener('click', async () => {
        if (!plantEditor || blocked || submitting || now() >= deadline) return;

        const row = plantEditor,
            source = plantSource.value,
            generation = ++plantGeneration;
        clearPlantPreview();

        if (!source.trim()) {
            plantStatus.textContent = 'Hãy nhập mã PlantUML.';
            return;
        }

        if (source.length > 20000) {
            plantStatus.textContent = 'Mã PlantUML không được vượt quá 20.000 ký tự.';
            return;
        }

        const controller = new AbortController();
        plantRequest?.abort();
        plantRequest = controller;
        const timeout = setTimeout(() => controller.abort(), 20000);
        const payload = new URLSearchParams();

        for (const name of ['csrf', 'assignmentId', 'submissionId']) payload.set(name, form.elements[name].value);

        payload.set('taskOrder', row.dataset.task);
        payload.set('requirementOrder', row.dataset.requirement);
        payload.set('source', source);
        plantRender.disabled = true;
        plantStatus.textContent = 'Đang dựng sơ đồ…';

        try {
            const response = await fetch(plantUrl, {
                method: 'POST',
                body: payload,
                credentials: 'same-origin',
                cache: 'no-store',
                signal: controller.signal
            });
            const data = response.headers.get('content-type')?.includes('application/json') ? await response
            .json() : null;

            if (!response.ok || !data) throw new Error(data?.error ||
                'Không tạo được sơ đồ. Kiểm tra kết nối và đăng nhập rồi thử lại.');

            if (typeof data.source !== 'string' || typeof data.image !== 'string') throw new Error(
                'Máy chủ chưa trả về sơ đồ hợp lệ.');

            const image = await croppedPng(data.image);

            if (generation !== plantGeneration || row !== plantEditor || blocked || submitting || now() >=
                deadline) return;

            // Retain exactly the source represented by this preview; edits invalidate it.
            plantSource.value = data.source;
            renderedPlantSource = data.source;
            renderedPlantImage = image;
            plantPreview.src = image;
            plantPreview.hidden = false;
            plantApply.disabled = false;
            plantStatus.textContent = 'Đã tạo ảnh xem trước. Nhấn Đưa vào bài làm để lưu sơ đồ.';
        } catch (error) {
            if (generation === plantGeneration && row === plantEditor)
                plantStatus.textContent = error.name === 'AbortError' ?
                'Dựng sơ đồ quá lâu. Hãy rút gọn mã rồi thử lại.' : error.message;
        } finally {
            clearTimeout(timeout);

            if (generation === plantGeneration && row === plantEditor) {
                plantRequest = null;
                plantRender.disabled = false;
            }
        }
    });

    plantApply.addEventListener('click', () => {
        if (!plantEditor || blocked || submitting || now() >= deadline || !renderedPlantImage || plantSource.value !==
            renderedPlantSource) return;

        const row = plantEditor;
        row.querySelector('.diagram-plantuml').value = renderedPlantSource;
        row.querySelector('.diagram-xml').value = '';
        row.querySelector('.diagram-image').value = renderedPlantImage;
        const preview = row.querySelector('.diagram-preview');
        preview.src = renderedPlantImage;
        preview.hidden = false;
        row.querySelector('.remove-diagram').hidden = false;
        closePlantEditor(true);
        changed();
        save();
    });

    document.getElementById('plantuml-close').addEventListener('click', () => closePlantEditor());

    plantDialog.addEventListener('cancel', event => {
        event.preventDefault();
        closePlantEditor();
    });

    requirements.forEach(row => {
        row.querySelector('.essay-text')?.addEventListener('input', changed);
        row.querySelector('.open-diagram')?.addEventListener('click', () => openEditor(row));
        row.querySelector('.open-plantuml')?.addEventListener('click', () => openPlantEditor(row));
        row.querySelector('.remove-diagram')?.addEventListener('click', () => {
            if (blocked || submitting || !confirm('Xóa sơ đồ khỏi yêu cầu này?')) return;

            row.querySelector('.diagram-xml').value = '';
            row.querySelector('.diagram-image').value = '';
            row.querySelector('.diagram-plantuml').value = '';
            row.querySelector('.diagram-preview').removeAttribute('src');
            row.querySelector('.diagram-preview').hidden = true;
            row.querySelector('.remove-diagram').hidden = true;
            changed();
        });
    });

    document.getElementById('diagram-close').addEventListener('click', () => closeEditor());

    dialog.addEventListener('cancel', event => {
        event.preventDefault();
        closeEditor();
    });

    document.getElementById('essay-save').addEventListener('click', () => {
        dirty = true;
        save();
    });

    form.addEventListener('submit', event => {
        event.preventDefault();

        if (now() >= deadline || confirm('Nộp bài tự luận? Sau khi nộp bạn không thể sửa bài làm.')) finish();
    });

    window.addEventListener('beforeunload', event => {
        if ((dirty || savePromise || activeEditor || plantEditor) && !completed) {
            event.preventDefault();
            event.returnValue = '';
        }
    });

    window.addEventListener('online', () => now() >= deadline ? finish() : save());

    function tick() {
        const seconds = Math.max(0, Math.ceil((deadline - now()) / 1000));
        clock.textContent = [Math.floor(seconds / 3600), Math.floor(seconds % 3600 / 60), seconds % 60].map(n => String(n)
            .padStart(2, '0')).join(':');
        clock.parentElement.classList.toggle('is-ending', seconds <= 60);

        if (!seconds) finish();
    }

    tick();
    save();
    setInterval(tick, 250);
})();
