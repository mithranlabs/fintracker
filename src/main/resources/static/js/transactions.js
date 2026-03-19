function loadCategories() {

    fetch("/categories")
        .then(res => res.json())
        .then(data => {

            const select = document.getElementById("editCategory");

            select.innerHTML = "";

            data.forEach(c => {

                const opt = document.createElement("option");

                opt.value = c.id;
                opt.text = c.name;

                select.appendChild(opt);

            });

        });

}
function deleteTx(id) {

    if (!confirm("Delete this transaction?")) return;

    fetch("/transactions/" + id, {
        method: "DELETE"
    })
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

    const categoryId = document.getElementById("editCategory").value;

    fetch("/transactions/" + id, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            amount: amount,
            type: type,
            note: note,
            category: { id: categoryId }
        })
    })
        .then(() => location.reload());

}
function closeEdit() {
    document.getElementById("editBox").style.display = "none";
}
fetch("/transactions")
    .then(res => res.json())
    .then(data => {

        const table = document.querySelector("#txTable tbody");

        data.forEach(tx => {

            const row = document.createElement("tr");

            row.innerHTML = `
                <td>${tx.id}</td>
                <td>${tx.amount}</td>
                <td>${tx.type}</td>
                <td>${tx.category ? tx.category.name : ""}</td>
                <td>${tx.note}</td>
                <td>${tx.date}</td>
                <td><button onclick="editTx(${tx.id}, ${tx.amount}, '${tx.type}', '${tx.note}')">Edit</button></td>
                <td><button onclick="deleteTx(${tx.id})">Delete</button></td>
            `;

            table.appendChild(row);
        });

    });
