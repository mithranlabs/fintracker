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
