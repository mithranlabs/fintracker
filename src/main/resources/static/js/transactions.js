function loadCategories() {
    fetch("/categories")
        .then(res => res.json())
        .then(data => {
            const list = document.getElementById("categoryList");
            list.innerHTML = "";
            data.forEach(c => {
                const opt = document.createElement("option");
                opt.value = c.name;
                list.appendChild(opt);
            });
        });
}

function deleteTx(id) {
    if (!confirm("Delete this transaction?")) return;
    fetch("/transactions/" + id, { method: "DELETE" })
        .then(() => location.reload());
}

function clearAll() {
    if (!confirm("Delete ALL transactions?")) return;
    fetch("/transactions/clear", { method: "DELETE" })
        .then(() => location.reload());
}

function editTx(id, amount, type, note) {
    document.getElementById("editBox").style.display = "block";
    document.getElementById("editId").value = id;
    document.getElementById("editAmount").value = amount;
    document.getElementById("editType").value = type;
    document.getElementById("editNote").value = note;
    loadCategories();
}

function saveEdit() {
    const id = document.getElementById("editId").value;
    const amount = document.getElementById("editAmount").value;
    const type = document.getElementById("editType").value;
    const note = document.getElementById("editNote").value;
    const categoryName = document.getElementById("editCategory").value;
    const applyAll = document.getElementById("applyAll").checked;
    const updateRule = document.getElementById("updateRule").checked;

    fetch("/transactions/" + id +
        "?applyAll=" + applyAll +
        "&updateRule=" + updateRule,
        {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                amount: amount,
                type: type,
                note: note,
                category: { name: categoryName }
            })
        })
        .then(() => location.reload());
}

function closeEdit() {
    document.getElementById("editBox").style.display = "none";
}


function renderTransactions(data) {
    const table = document.querySelector("#txTable tbody");
    table.innerHTML = "";

    if (data.length === 0) {
        table.innerHTML = `<tr><td colspan="8" style="text-align:center">No transactions found.</td></tr>`;
        return;
    }

    data.forEach(tx => {
        const categoryName = tx.category
            ? (typeof tx.category === "string" ? tx.category : tx.category.name)
            : "Uncategorized";

        const row = document.createElement("tr");
        row.setAttribute("data-id", tx.id);
        row.innerHTML = `
            <td>${tx.id}</td>
            <td>${tx.amount}</td>
            <td>${tx.type}</td>
            <td>${categoryName}</td>
            <td>${tx.note}</td>
            <td>${tx.date}</td>
            <td>
                <button class="recurring-btn ${tx.recurring ? 'recurring-active' : ''}"
                    title="${tx.recurring ? 'Recurring (click to unmark)' : 'Mark as recurring'}"
                    onclick="toggleRecurring(${tx.id}, this)">
                    ${tx.recurring ? '🔁' : '↩'}
                </button>
            </td>
            <td class="action-cell">
                <button onclick="editTx(${tx.id}, ${tx.amount}, '${tx.type}', '${tx.note}')">Edit</button>
                <button onclick="deleteTx(${tx.id})">Delete</button>
            </td>
        `;
        table.appendChild(row);
    });
}




function loadTransactions() {
    fetch("/transactions")
        .then(res => res.json())
        .then(data => renderTransactions(data));
}


function applyFilter() {
    const start = document.getElementById("filterStart").value;
    const end = document.getElementById("filterEnd").value;

    if (!start || !end) {
        alert("Please select both start and end dates.");
        return;
    }
    if (new Date(start) > new Date(end)) {
        alert("Start date cannot be after end date.");
        return;
    }

    fetch(`/transactions/filter?start=${start}&end=${end}`)
        .then(res => res.json())
        .then(data => {
            document.getElementById("filterInfo").textContent =
                `Showing ${data.length} transaction(s) from ${start} to ${end}`;
            renderTransactions(data);
            updateExportLink();
        });
}

function clearFilter() {
    document.getElementById("filterStart").value = "";
    document.getElementById("filterEnd").value = "";
    document.getElementById("filterInfo").textContent = "";
    loadTransactions();
    updateExportLink();
}
function updateExportLink() {
    const start = document.getElementById('filterStart')?.value;
    const end = document.getElementById('filterEnd')?.value;
    const btn = document.getElementById('exportBtn');
    if (start && end) {
        btn.href = `/transactions/export/csv?start=${start}&end=${end}`;
    } else {
        btn.href = '/transactions/export/csv';
    }
}
function toggleRecurring(id, btn) {
    fetch(`/transactions/${id}/recurring`, { method: "POST" })
        .then(res => res.json())
        .then(data => {
            btn.textContent = data.recurring ? "🔁" : "↩";
            btn.title = data.recurring ? "Recurring (click to unmark)" : "Mark as recurring";
            btn.classList.toggle("recurring-active", data.recurring);
        });
}

function loadRecurringSuggestions() {
    fetch("/transactions/recurring/suggestions")
        .then(res => res.json())
        .then(data => {
            const banner = document.getElementById("recurringSuggestions");
            if (!data.length) { banner.style.display = "none"; return; }

            banner.style.display = "block";
            banner.innerHTML = `<strong>🔁 ${data.length} recurring transaction(s) due this month:</strong><br>` +
                data.map(t => `
                    <span class="suggestion-chip"
                        onclick="window.location.href='/add-page?note=${encodeURIComponent(t.note)}&amount=${t.amount}&type=${t.type}'">
                        ${t.note} — ₹${t.amount} (${t.type})
                    </span>
                `).join("");
        });
}


loadTransactions();
updateExportLink();
loadRecurringSuggestions();
