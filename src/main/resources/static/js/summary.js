// CATEGORY SUMMARY

fetch("/summary/category")
    .then(res => res.json())
    .then(data => {

        let expenseLabels = []
        let expenseValues = []

        let incomeLabels = []
        let incomeValues = []

        data.forEach(r => {

            if (r.type === "expense") {
                expenseLabels.push(r.category)
                expenseValues.push(r.total)
            }

            if (r.type === "income") {
                incomeLabels.push(r.category)
                incomeValues.push(r.total)
            }

        })
        const table =
            document.querySelector("#categoryTable tbody");

        if (table) {

            table.innerHTML = "";

            data.forEach(r => {

                if (r.type !== "expense") return;

                const row = document.createElement("tr");

                row.innerHTML = `
            <td>${r.category}</td>
            <td>${r.total}</td>
        `;

                table.appendChild(row);

            });

        }

        new Chart(
            document.getElementById("expenseChart"),
            {
                type: "pie",
                data: {
                    labels: expenseLabels,
                    datasets: [{
                        data: expenseValues
                    }]
                    },
                options: {
                    responsive: true,
                    maintainAspectRatio: false
                }

            }
        )

        new Chart(
            document.getElementById("incomeChart"),
            {
                type: "pie",
                data: {
                    labels: incomeLabels,
                    datasets: [{
                        data: incomeValues
                    }]

                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false
                }
            }
        )

    })

// TYPE SUMMARY

fetch("/summary/type")
    .then(res => res.json())
    .then(data => {

        const table = document.querySelector("#typeTable tbody");

        const labels = [];
        const values = [];

        data.forEach(row => {

            const tr = document.createElement("tr");

            tr.innerHTML = `
                <td>${row.type}</td>
                <td>${row.total}</td>
            `;

            table.appendChild(tr);

            labels.push(row.type);
            values.push(row.total);

        });

        new Chart(
            document.getElementById("typeChart"),
            {
                type: "doughnut",
                data: {
                    labels: labels,
                    datasets: [{
                        data: values
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false
                }

            }
        );

    });
fetch("/summary/merchant")
    .then(res => res.json())
    .then(data => {

        const labels = [];
        const values = [];

        data.slice(0, 10).forEach(row => {

            labels.push(row.merchant);
            values.push(row.total);

        });

        new Chart(
            document.getElementById("merchantChart"),
            {
                type: "bar",
                data: {
                    labels: labels,
                    datasets: [{
                        label: "Amount",
                        data: values
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: {
                            display: false
                        },
                        tooltip: {
                            enabled: true
                        }
                    },
                    scales: {
                        x: {
                            ticks: {
                                display: false   // hide labels
                            },
                            grid: {
                                display: false
                            }
                        }
                    }
                }
            }
        );

    });
fetch("/summary/month")
    .then(res => res.json())
    .then(data => {

        let months = []

        let expense = []
        let income = []

        data.forEach(r => {

            if (!months.includes(r.month)) {
                months.push(r.month)
            }

        })

        months.forEach(m => {

            let e = 0
            let i = 0

            data.forEach(r => {

                if (r.month === m && r.type === "expense") {
                    e = r.total
                }

                if (r.month === m && r.type === "income") {
                    i = r.total
                }

            })

            expense.push(e)
            income.push(i)

        })


        new Chart(
            document.getElementById("monthChart"),
            {
                type: "bar",

                data: {
                    labels: months,

                    datasets: [
                        {
                            label: "Expense",
                            data: expense
                        },
                        {
                            label: "Income",
                            data: income
                        }
                    ]
                },

                options: {
                    responsive: true,
                    maintainAspectRatio: false
                }

            }
        )

    })
async function loadCategories() {
    const res = await fetch("/categories");
    const data = await res.json();
    const select = document.getElementById("budgetCategory");
    select.innerHTML = "";
    data.forEach(cat => {
        const opt = document.createElement("option");
        opt.value = cat.id;
        opt.textContent = cat.name;
        select.appendChild(opt);
    });
}

async function loadBudgets() {
    const month = document.getElementById("budgetMonth").value;
    if (!month) return;

    const res = await fetch(`/budgets?month=${month}`);
    if (res.status === 401) { window.location.href = "/login.html"; return; }

    const data = await res.json();
    const tbody = document.getElementById("budgetBody");
    const table = document.getElementById("budgetTable");
    const noMsg = document.getElementById("noBudgetMsg");

    tbody.innerHTML = "";

    if (data.length === 0) {
        noMsg.style.display = "block";
        table.style.display = "none";
        return;
    }

    noMsg.style.display = "none";
    table.style.display = "table";

    data.forEach(b => {
        const pct = b.limitAmount > 0 ? (b.spent / b.limitAmount) * 100 : 0;
        let statusClass = "status-ok";
        let statusText = "✅ OK";
        if (pct >= 100) { statusClass = "status-over"; statusText = "🔴 Over"; }
        else if (pct >= 80) { statusClass = "status-warn"; statusText = "🟡 Warning"; }

        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${b.category}</td>
            <td>₹${b.limitAmount.toLocaleString("en-IN")}</td>
            <td>₹${b.spent.toLocaleString("en-IN")}</td>
            <td>₹${Math.max(0, b.remaining).toLocaleString("en-IN")}</td>
            <td class="${statusClass}">${statusText}</td>
            <td><button class="btn-delete-budget" onclick="deleteBudget(${b.id})">Remove</button></td>
        `;
        tbody.appendChild(tr);
    });
}

async function setBudget() {
    const categoryId = document.getElementById("budgetCategory").value;
    const limitAmount = document.getElementById("budgetAmount").value;
    const month = document.getElementById("budgetMonth").value;
    const status = document.getElementById("budgetStatus");

    if (!categoryId || !limitAmount || !month) {
        status.textContent = "Fill all fields.";
        status.style.color = "#dc2626";
        return;
    }

    const res = await fetch("/budgets", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ categoryId, limitAmount, month })
    });

    if (res.ok) {
        status.textContent = "Budget saved!";
        status.style.color = "#16a34a";
        loadBudgets();
    } else {
        status.textContent = "Failed to save.";
        status.style.color = "#dc2626";
    }
}

async function deleteBudget(id) {
    if (!confirm("Remove this budget?")) return;
    const res = await fetch(`/budgets/${id}`, { method: "DELETE" });
    if (res.ok) loadBudgets();
}

// Set default month to current and load
const now = new Date();
const currentMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
document.getElementById("budgetMonth").value = currentMonth;
document.getElementById("budgetMonth").addEventListener("change", loadBudgets);

loadCategories();
loadBudgets();

