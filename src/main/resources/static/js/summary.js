// CATEGORY SUMMARY

fetch("/summary/category")
    .then(res => res.json())
    .then(data => {

        const table = document.querySelector("#catTable tbody");

        const labels = [];
        const values = [];

        data.forEach(row => {

            const tr = document.createElement("tr");

            tr.innerHTML = `
                <td>${row.category}</td>
                <td>${row.total}</td>
            `;

            table.appendChild(tr);

            labels.push(row.category);
            values.push(row.total);

        });


        // CHART

        new Chart(
            document.getElementById("catChart"),
            {
                type: "pie",
                data: {
                    labels: labels,
                    datasets: [{
                        data: values
                    }]
                }
            }
        );


    });

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