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
