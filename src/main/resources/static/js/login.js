function login() {

    fetch("/auth/login", {

        method: "POST",

        headers: {
            "Content-Type": "application/json"
        },

        body: JSON.stringify({

            username:
            document.getElementById("login").value,

            password:
            document.getElementById("password").value

        })

    })
        .then(r => r.text())
        .then(t => {

            alert(t)

            if (t === "OK") {
                window.location = "/"
            }

        })

}

