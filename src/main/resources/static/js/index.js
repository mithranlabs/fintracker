async function checkSession() {
    const res = await fetch('/auth/me');
    if (res.status === 401) window.location.href = '/login-page';
}

function fmt(amount) {
    return '₹' + amount.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

async function loadDashboard() {
    const res = await fetch('/dashboard/summary');
    if (res.status === 401) { window.location.href = '/login-page'; return; }

    const d = await res.json();

    document.getElementById('monthLabel').textContent = d.month;
    document.getElementById('welcomeMsg').textContent = `Hi, ${d.username} 👋`;


    document.getElementById('cardIncome').textContent  = fmt(d.income);
    document.getElementById('cardExpense').textContent = fmt(d.expense);

    const savingsEl = document.getElementById('cardSavings');
    savingsEl.textContent = fmt(d.savings);
    savingsEl.style.color = d.savings >= 0 ? '#16a34a' : '#dc2626';

    document.getElementById('cardCount').textContent = d.count;

    // budget bars
    if (d.budgets && d.budgets.length > 0) {
        document.getElementById('budgetSection').style.display = 'block';
        const barsDiv = document.getElementById('budgetBars');
        barsDiv.innerHTML = '';
        d.budgets.forEach(b => {
            const pct = Math.min(b.pct, 100);
            const color = b.pct >= 100 ? '#dc2626' : b.pct >= 80 ? '#d97706' : '#16a34a';
            barsDiv.innerHTML += `
                <div class="budget-bar-row">
                    <div class="budget-bar-label">${b.category}</div>
                    <div class="budget-bar-track">
                        <div class="budget-bar-fill" style="width:${pct}%; background:${color};"></div>
                    </div>
                    <div class="budget-bar-meta">${fmt(b.spent)} / ${fmt(b.limit)}</div>
                </div>
            `;
        });
    }

    // recent transactions
    const tbody = document.getElementById('recentBody');
    const table = document.getElementById('recentTable');
    const noMsg = document.getElementById('noRecentMsg');

    tbody.innerHTML = '';

    if (d.recent.length === 0) {
        noMsg.style.display = 'block';
        table.style.display = 'none';
    } else {
        noMsg.style.display = 'none';
        table.style.display = 'table';
        d.recent.forEach(t => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${t.note}</td>
                <td>${t.category}</td>
                <td>${fmt(t.amount)}</td>
                <td class="type-${t.type}">${t.type}</td>
                <td>${t.date}</td>
            `;
            tbody.appendChild(tr);
        });
    }
}
function formatInsight(text) {
    return text
        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
        .replace(/\*(.*?)\*/g, '<li>$1</li>')
        .replace(/(<li>.*<\/li>)/gs, '<ul>$1</ul>')
        .replace(/\n/g, '<br>');
}


function logout() {
    fetch('/auth/logout').then(() => window.location = '/login-page');
}
async function loadInsights() {
    const box = document.getElementById('insightBox');
    const loading = document.getElementById('insightLoading');
    const btn = document.getElementById('insightBtn');

    box.style.display = 'none';
    loading.style.display = 'block';
    btn.disabled = true;
    btn.textContent = 'Analyzing...';

    try {
        const res = await fetch('/insights');
        const data = await res.json();

        loading.style.display = 'none';
        box.style.display = 'block';
        box.innerHTML = formatInsight(data.insight);
        btn.textContent = 'Refresh Insights';
        btn.disabled = false;
    } catch (err) {
        loading.style.display = 'none';
        box.textContent = 'Failed to load insights.';
        box.style.display = 'block';
        btn.disabled = false;
        btn.textContent = 'Get Insights';
    }
}


checkSession();
loadDashboard();
