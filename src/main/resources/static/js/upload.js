function uploadFile() {
    const fileInput = document.getElementById("fileInput");
    const file = fileInput.files[0];
    const formData = new FormData();
    formData.append("file", file);
    const replace = document.getElementById("replaceOld").checked;
    formData.append("replace", replace);

    fetch("/upload", {
        method: "POST",
        body: formData
    })
        .then(res => res.text())
        .then(data => {
            document.getElementById("status").innerText = data;
            loadUploadHistory();
        });
}

async function loadUploadHistory() {
    const res = await fetch("/upload/history");
    if (res.status === 401) {
        window.location.href = "/login.html";
        return;
    }

    const data = await res.json();
    const tbody = document.getElementById("historyBody");
    const table = document.getElementById("historyTable");
    const noMsg = document.getElementById("noHistoryMsg");

    tbody.innerHTML = "";

    if (data.length === 0) {
        noMsg.style.display = "block";
        table.style.display = "none";
        return;
    }

    noMsg.style.display = "none";
    table.style.display = "table";

    data.forEach((upload, index) => {
        const date = new Date(upload.date).toLocaleDateString("en-IN", {
            day: "2-digit", month: "short", year: "numeric"
        });
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${index + 1}</td>
            <td>${upload.fileName}</td>
            <td>${date}</td>
            <td>${upload.transactionCount}</td>
            <td><button class="btn-delete-upload" onclick="deleteUploadRecord(${upload.id})">Delete</button></td>
        `;
        tbody.appendChild(tr);
    });
}

async function deleteUploadRecord(id) {
    if (!confirm("Remove this record from history?")) return;

    const res = await fetch(`/upload/delete/${id}`, { method: "DELETE" });
    if (res.ok) {
        loadUploadHistory();
    } else {
        alert("Failed to delete.");
    }
}

loadUploadHistory();
async function uploadSbi() {
    const file = document.getElementById('sbiFile').files[0];
    const password = document.getElementById('sbiPassword').value.trim();
    const replace = document.getElementById('sbiReplace').checked;
    const status = document.getElementById('sbiStatus');

    if (!file) { status.textContent = 'Please select a PDF file.'; return; }
    if (!password) { status.textContent = 'Please enter the PDF password.'; return; }

    const formData = new FormData();
    formData.append('file', file);
    formData.append('password', password);
    formData.append('replace', replace);

    status.textContent = 'Uploading...';

    try {
        const res = await fetch('/upload/sbi', { method: 'POST', body: formData });
        const text = await res.text();
        status.textContent = res.ok ? `✅ ${text}` : `❌ ${text}`;
    } catch (e) {
        status.textContent = '❌ Network error';
    }
}

function logout() {
    fetch('/auth/logout').then(() => window.location = '/login-page');
}

// ═══════════════════════════════════════════════════════════════════════════
// Universal Parser Functions
// ═══════════════════════════════════════════════════════════════════════════

let parsedTransactions = [];
let uploadedFileName = '';

async function uploadUniversal() {
    const file = document.getElementById('universalFile').files[0];
    const replace = document.getElementById('universalReplace').checked;
    const status = document.getElementById('universalStatus');
    const preview = document.getElementById('universalPreview');

    if (!file) {
        status.textContent = 'Please select a file.';
        return;
    }

    uploadedFileName = file.name;
    const formData = new FormData();
    formData.append('file', file);

    status.textContent = '⏳ Parsing statement...';
    preview.style.display = 'none';

    try {
        const res = await fetch('/upload/universal', { method: 'POST', body: formData });
        const data = await res.json();

        if (!data.success) {
            status.textContent = `❌ ${data.message}`;
            return;
        }

        // Store parsed transactions
        parsedTransactions = data.transactions;

        // Show preview
        displayUniversalPreview(data);
        status.textContent = `✅ ${data.message}`;
        preview.style.display = 'block';

    } catch (e) {
        status.textContent = '❌ Network error or parsing failed';
        console.error(e);
    }
}

function displayUniversalPreview(data) {
    const tbody = document.getElementById('previewTableBody');
    const summary = document.getElementById('previewSummary');
    const reviewWarning = document.getElementById('reviewWarning');
    const reviewCount = document.getElementById('reviewCount');

    tbody.innerHTML = '';

    // Show summary
    const method = data.parsingMethod === 'HEURISTIC' ? '🤖 Heuristic' : '🧠 AI-assisted';
    summary.innerHTML = `
        <strong>Total:</strong> ${data.totalTransactions} transactions | 
        <strong>Method:</strong> ${method} | 
        <strong>Needs Review:</strong> ${data.needsReviewCount}
    `;

    // Show review warning if needed
    if (data.needsReviewCount > 0) {
        reviewWarning.style.display = 'block';
        reviewCount.textContent = `${data.needsReviewCount} transaction(s) have UNKNOWN direction.`;
    } else {
        reviewWarning.style.display = 'none';
    }

    // Build table rows
    data.transactions.forEach((txn, index) => {
        const tr = document.createElement('tr');
        tr.style.borderBottom = '1px solid #eee';

        // Confidence badge
        const confPercent = Math.round(txn.confidence * 100);
        let confBadge = '';
        if (txn.confidence >= 0.8) {
            confBadge = `<span style="background: #d4edda; color: #155724; padding: 2px 6px; border-radius: 3px; font-size: 0.85em;">${confPercent}%</span>`;
        } else if (txn.confidence >= 0.6) {
            confBadge = `<span style="background: #fff3cd; color: #856404; padding: 2px 6px; border-radius: 3px; font-size: 0.85em;">${confPercent}%</span>`;
        } else {
            confBadge = `<span style="background: #f8d7da; color: #721c24; padding: 2px 6px; border-radius: 3px; font-size: 0.85em;">${confPercent}%</span>`;
        }

        // Direction dropdown
        const directionSelect = `
            <select onchange="updateDirection(${index}, this.value)" 
                    style="padding: 4px 8px; border: 1px solid ${txn.needsReview ? '#dc3545' : '#ced4da'}; border-radius: 4px; background: ${txn.needsReview ? '#fff3cd' : 'white'};">
                <option value="DEBIT" ${txn.direction === 'DEBIT' ? 'selected' : ''}>Debit ↗</option>
                <option value="CREDIT" ${txn.direction === 'CREDIT' ? 'selected' : ''}>Credit ↘</option>
                <option value="UNKNOWN" ${txn.direction === 'UNKNOWN' ? 'selected' : ''}>❓ Unknown</option>
            </select>
        `;

        // Amount formatting
        const amountFormatted = '₹' + txn.amount.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

        tr.innerHTML = `
            <td style="padding: 8px;">${txn.date}</td>
            <td style="padding: 8px; max-width: 300px; overflow: hidden; text-overflow: ellipsis;" title="${txn.narration}">${txn.narration}</td>
            <td style="padding: 8px; text-align: right; font-weight: bold;">${amountFormatted}</td>
            <td style="padding: 8px; text-align: center;">${directionSelect}</td>
            <td style="padding: 8px; text-align: center;">${confBadge}</td>
        `;

        tbody.appendChild(tr);
    });
}

function updateDirection(index, newDirection) {
    parsedTransactions[index].direction = newDirection;
    parsedTransactions[index].needsReview = (newDirection === 'UNKNOWN');

    // Recount needs review
    const needsReview = parsedTransactions.filter(t => t.needsReview).length;
    const reviewWarning = document.getElementById('reviewWarning');
    const reviewCount = document.getElementById('reviewCount');

    if (needsReview > 0) {
        reviewWarning.style.display = 'block';
        reviewCount.textContent = `${needsReview} transaction(s) have UNKNOWN direction.`;
    } else {
        reviewWarning.style.display = 'none';
    }
}

async function saveUniversalTransactions() {
    const replace = document.getElementById('universalReplace').checked;
    const status = document.getElementById('universalStatus');
    const saveBtn = document.getElementById('saveBtn');

    // Check for UNKNOWN directions
    const unknownCount = parsedTransactions.filter(t => t.needsReview).length;
    if (unknownCount > 0) {
        status.textContent = `❌ Cannot save: ${unknownCount} transaction(s) still have UNKNOWN direction. Please correct them first.`;
        return;
    }

    saveBtn.disabled = true;
    status.textContent = '⏳ Saving transactions...';

    try {
        const res = await fetch('/upload/universal/save', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                transactions: parsedTransactions,
                replaceExisting: replace,
                fileName: uploadedFileName
            })
        });

        const text = await res.text();

        if (res.ok) {
            status.textContent = `✅ ${text}`;
            document.getElementById('universalPreview').style.display = 'none';
            document.getElementById('universalFile').value = '';
            loadUploadHistory();
        } else {
            status.textContent = `❌ ${text}`;
            saveBtn.disabled = false;
        }

    } catch (e) {
        status.textContent = '❌ Failed to save transactions';
        saveBtn.disabled = false;
        console.error(e);
    }
}
