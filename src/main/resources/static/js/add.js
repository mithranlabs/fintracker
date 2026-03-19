loadCategories();

function loadCategories() {

    fetch("/categories")
        .then(res => res.json())
        .then(data => {

            const select = document.getElementById("categorySelect");

            select.innerHTML = "";

            data.forEach(c => {

                const opt = document.createElement("option");

                opt.value = c.id;
                opt.text = c.name;

                select.appendChild(opt);

            });

        });

}



document.getElementById("categorySearch")
    .addEventListener("input", function () {

        const text = this.value.toLowerCase();

        fetch("/categories")
            .then(res => res.json())
            .then(data => {

                const select = document.getElementById("categorySelect");

                select.innerHTML = "";

                data.forEach(c => {

                    if (c.name.toLowerCase().includes(text)) {

                        const opt = document.createElement("option");

                        opt.value = c.id;
                        opt.text = c.name;

                        select.appendChild(opt);

                    }

                });

            });

    });



function createCategory() {

    const name = prompt("Category name?");
    const type = prompt("Type? expense / income");

    fetch("/categories", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            name: name,
            type: type
        })
    })
        .then(() => loadCategories());

}



function addTx() {

    const amount = document.getElementById("amount").value;
    const type = document.getElementById("type").value;
    const note = document.getElementById("note").value;
    const date = document.getElementById("date").value;
    const categoryId =
        document.getElementById("categorySelect").value;

    const tx = {
        amount: amount,
        type: type,
        note: note,
        date: date,
        category: {
            id: categoryId
        }
    };

    fetch("/transactions", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(tx)
    })
        .then(res => res.json())
        .then(() => {

            document.getElementById("status").innerText =
                "Saved";

        });

}