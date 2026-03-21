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

