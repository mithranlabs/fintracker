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

        });

}